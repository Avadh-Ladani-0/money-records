package com.moneyrecords.app.ui.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.lock.AppLockPrefs
import com.moneyrecords.app.domain.lock.LockPolicy
import com.moneyrecords.app.security.AppLockManager
import com.moneyrecords.app.security.canUseScreenLock
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.SwitchRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(val lock: AppLockManager) : ViewModel()

/**
 * Settings → App lock. Turning the lock on or off is confirmed with the phone's screen lock; if the
 * phone has none, it explains and links to the phone's security settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLockSettingsScreen(onBack: () -> Unit, viewModel: AppLockSettingsViewModel = hiltViewModel()) {
    val lock = viewModel.lock
    val s by lock.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    var needScreenLock by remember { mutableStateOf(false) }

    fun setEnabled(on: Boolean) {
        if (on && !canUseScreenLock(context)) { needScreenLock = true; return }
        lock.confirm(activity, if (on) "Turn on app lock" else "Turn off app lock") { ok ->
            if (ok) scope.launch { lock.update { it.copy(enabled = on) } }
        }
    }
    fun update(change: (AppLockPrefs) -> AppLockPrefs) = scope.launch { lock.update(change) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "App lock", onBack = onBack) }
        item {
            SwitchRow(
                title = "Lock with screen lock",
                subtitle = "Fingerprint, face or your phone's PIN/pattern",
                icon = Icons.Outlined.Fingerprint,
                checked = s.enabled,
                onCheckedChange = ::setEnabled,
            )
        }
        if (s.enabled) {
            item { Spacer(Modifier.height(Space.lg)); SectionLabel("Lock again after leaving the app") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AppLockPrefs.RELOCK_CHOICES.forEach { sec ->
                        ChoiceChip(LockPolicy.label(sec), selected = s.relockAfterSeconds == sec,
                            onClick = { update { it.copy(relockAfterSeconds = sec) } })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Privacy") }
        item {
            SwitchRow(
                title = "Hide in recent apps",
                subtitle = "Blank preview in the app switcher; also blocks screenshots",
                icon = Icons.Outlined.VisibilityOff,
                checked = s.hideInRecents,
                onCheckedChange = { on -> update { it.copy(hideInRecents = on) } },
            )
        }
        item {
            Text(
                "On a locked phone, MoneyRecords notifications don't show amounts — the full text appears once you unlock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier,
            )
        }
    }

    if (needScreenLock) {
        AlertDialog(
            onDismissRequest = { needScreenLock = false },
            title = { Text("Set a screen lock first") },
            text = { Text("App lock uses your phone's fingerprint, face or PIN. Add one in your phone's security settings, then come back.") },
            confirmButton = {
                TextButton(onClick = {
                    needScreenLock = false
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { needScreenLock = false }) { Text("Cancel") } },
        )
    }
}
