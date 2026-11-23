package com.moneyrecords.app.ui.feature.detect

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.detect.DetectionPrefs
import com.moneyrecords.app.detect.DetectionSettings
import com.moneyrecords.app.detect.PaymentApp
import com.moneyrecords.app.detect.PaymentApps
import com.moneyrecords.app.detect.PaymentNotificationListener
import com.moneyrecords.app.detect.SmsBackfill
import com.moneyrecords.app.domain.detect.Detected
import com.moneyrecords.app.domain.detect.DetectionText
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SwitchRow
import com.moneyrecords.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutoDetectState(
    val prefs: DetectionPrefs = DetectionPrefs(),
    val accounts: List<Account> = emptyList(),
    val log: List<Pair<Detected, String>> = emptyList(),
)

@HiltViewModel
class AutoDetectViewModel @Inject constructor(
    private val settings: DetectionSettings,
    private val backfill: SmsBackfill,
    accounts: AccountRepository,
    repository: RoomDetectionRepository,
) : ViewModel() {
    val state: StateFlow<AutoDetectState> = combine(settings.flow, accounts.observeAccounts(), repository.observeLog()) { p, a, log ->
        val names = a.associate { it.id to it.name }
        AutoDetectState(p, a, log.map { it to DetectionText.title(it, names) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoDetectState())

    fun setSms(on: Boolean, onCaughtUp: (Int) -> Unit = {}) {
        viewModelScope.launch {
            settings.setSms(on)
            if (on) onCaughtUp(backfill.run())
        }
    }
    fun setNotifications(on: Boolean) { viewModelScope.launch { settings.setNotifications(on) } }
    fun setLinked(pkg: String, id: Long, linked: Boolean) { viewModelScope.launch { settings.setLinked(pkg, id, linked) } }
    fun setUsual(pkg: String, id: Long) { viewModelScope.launch { settings.setUsual(pkg, id) } }
}

private fun hasSms(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

private fun usageAccess(context: Context): Boolean {
    val ops = context.getSystemService(android.app.AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) ==
        android.app.AppOpsManager.MODE_ALLOWED
}

private fun hasListener(context: Context) = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/**
 * Settings → Auto-detect: catch payments from bank SMS and payment-app notifications. Nothing is
 * recorded on its own — every catch is a prompt (Add / Later / Ignore). All on this phone.
 */
@Composable
fun AutoDetectScreen(onBack: () -> Unit, onMessage: (String) -> Unit, viewModel: AutoDetectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var smsGranted by remember { mutableStateOf(hasSms(context)) }
    var listenerOn by remember { mutableStateOf(hasListener(context)) }
    val apps = remember { PaymentApps.installed(context) }
    LifecycleResumeEffect(Unit) {
        smsGranted = hasSms(context)
        listenerOn = hasListener(context)
        if (listenerOn && !state.prefs.notifications) viewModel.setNotifications(true)
        onPauseOrDispose { }
    }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsGranted = result.values.all { it }
        if (smsGranted) viewModel.setSms(true) { n -> onMessage(if (n > 0) "Found $n payments in the last 2 days — see To record" else "SMS detection is on") }
        else onMessage("SMS permission wasn't given — see the note below")
    }
    fun openAppInfo() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "Auto-detect", subtitle = "Catch payments so you don't forget to record them", onBack = onBack) }
        item {
            SwitchRow(
                title = "Bank SMS",
                subtitle = "Reads only SMS from bank senders (like AD-SBIUPI) — never messages from people",
                icon = Icons.Outlined.Sms,
                checked = state.prefs.sms && smsGranted,
                onCheckedChange = { on ->
                    if (!on) viewModel.setSms(false)
                    else if (smsGranted) viewModel.setSms(true) { n -> if (n > 0) onMessage("Found $n payments in the last 2 days — see To record") }
                    else askSms.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                },
            )
        }
        item {
            SwitchRow(
                title = "Payment app notifications",
                subtitle = "Google Pay, PhonePe, BHIM, Paytm and bank apps only — helps with UPI Lite",
                icon = Icons.Outlined.NotificationsActive,
                checked = state.prefs.notifications && listenerOn,
                onCheckedChange = { on ->
                    if (on && !listenerOn) {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, PaymentNotificationListener::class.java).flattenToString()),
                        )
                    }
                    viewModel.setNotifications(on)
                },
            )
        }
        item {
            val watcher = remember { usageAccess(context) }
            var usageOn by remember { mutableStateOf(watcher) }
            LifecycleResumeEffect(Unit) { usageOn = usageAccess(context); onPauseOrDispose { } }
            ListRow(
                title = "Usage access",
                subtitle = if (usageOn) "On — after a Scan & pay tap, you're asked as soon as you leave the payment app"
                else "Off — turn on to be asked as soon as you swipe away from the payment app after a Scan & pay tap",
                subtitleLines = 2,
                icon = Icons.Outlined.NotificationsActive,
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                trailing = { Text(if (usageOn) "On" else "Turn on", style = MaterialTheme.typography.labelLarge) },
            )
        }
        item {
            Column(Modifier.padding(horizontal = Space.xs, vertical = Space.sm)) {
                Text(
                    "If Android says a setting is restricted: open App info → ⋮ → Allow restricted settings, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = ::openAppInfo) { Text("Open App info", color = MaterialTheme.colorScheme.onBackground) }
            }
        }

        item {
            Spacer(Modifier.height(Space.lg)); SectionLabel("Accounts linked in each app")
            Text(
                "Tick every account an app can pay from. A bank SMS always tells which one paid; without one, a small payment " +
                    "from the app that has UPI Lite is from UPI Lite, otherwise from the app's usual account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.sm),
            )
        }
        items(apps, key = { "app-${it.pkg}" }) { app -> AppLinksRow(app, state, viewModel) }
        item {
            val lite = state.accounts.firstOrNull { it.isUpiLite }
            Text(
                if (lite == null) "UPI Lite isn't set up. To track it, add an account of type UPI Lite (Accounts → +)."
                else "UPI Lite: “${lite.name}”, in ${lite.liteApp?.let { PaymentApps.nameOf(it) } ?: "an app"}, " +
                    "funded by ${state.accounts.firstOrNull { it.id == lite.fundingAccountId }?.name ?: "a bank"}. " +
                    "Its top-up SMS become transfers into it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.md),
            )
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Detection log") }
        if (state.log.isEmpty()) item {
            Text("Nothing caught yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.xs))
        }
        items(state.log, key = { "log-${it.first.id}" }) { (d, title) ->
            ListRow(title = title, subtitle = d.status.name.lowercase().replaceFirstChar { it.uppercase() } + " · " + d.text, subtitleLines = 2)
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppLinksRow(app: PaymentApp, state: AutoDetectState, viewModel: AutoDetectViewModel) {
    val banks = state.accounts.filter { !it.isUpiLite && !it.archived }
    val linked = state.prefs.appLinks[app.pkg].orEmpty()
    val usual = state.prefs.appUsual[app.pkg]
    val lite = state.accounts.firstOrNull { it.isUpiLite && it.liteApp == app.pkg }
    Column(Modifier.padding(vertical = Space.sm), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs)) {
        Text(app.name + if (lite != null) " · has UPI Lite" else "", style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
        ) {
            banks.forEach { a ->
                com.moneyrecords.app.ui.components.ChoiceChip(
                    a.name + if (a.id == usual) " · usually" else "",
                    selected = a.id in linked,
                    onClick = {
                        // Tap: link / unlink. Tapping a linked one that isn't "usually" makes it usual.
                        when {
                            a.id !in linked -> viewModel.setLinked(app.pkg, a.id, true)
                            a.id != usual && linked.size > 1 -> viewModel.setUsual(app.pkg, a.id)
                            else -> viewModel.setLinked(app.pkg, a.id, false)
                        }
                    },
                )
            }
        }
        if (linked.size > 1) {
            Text(
                "Tap a ticked account to make it “usually”; tap it again to unlink.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
