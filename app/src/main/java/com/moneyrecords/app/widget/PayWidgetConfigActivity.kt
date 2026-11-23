package com.moneyrecords.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.moneyrecords.app.detect.DetectionSettings
import com.moneyrecords.app.detect.PaymentApps
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.ui.components.AccountDropdown
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.theme.AppTheme
import com.moneyrecords.app.ui.theme.MoneyRecordsTheme
import com.moneyrecords.app.ui.theme.Space
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Set up a "Scan & pay" widget: which payment app, and which account it pays from. */
@AndroidEntryPoint
class PayWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var accounts: AccountRepository
    @Inject lateinit var prefs: UserPrefsRepository
    @Inject lateinit var detection: DetectionSettings

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val apps = PaymentApps.installed(this)
        val themes = prefs.observeTheme().map { k -> AppTheme.entries.firstOrNull { it.name == k } ?: AppTheme.NOTHING }

        setContent {
            val theme by themes.collectAsState(initial = AppTheme.NOTHING)
            var app by remember { mutableStateOf(apps.firstOrNull()?.pkg) }
            MoneyRecordsTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.statusBarsPadding().navigationBarsPadding().padding(Space.screen).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Text("Scan & pay widget", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Tapping it opens the app on its scanner; when you come back, MoneyRecords asks you to record the payment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SectionLabel("Payment app")
                        if (apps.isEmpty()) Text("No UPI apps found on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            apps.forEach { a -> ChoiceChip(a.name, selected = app == a.pkg, onClick = { app = a.pkg }) }
                        }
                        app?.let { pkg ->
                            Text(
                                if (com.moneyrecords.app.detect.ScanRoutes.opensScanner(pkg)) "Opens straight on this app's QR scanner."
                                else "This app doesn't let other apps open its scanner, so it opens on its home screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "The account is worked out after each payment: the bank's SMS names it; with no SMS, a small payment " +
                                "from the app that has UPI Lite is from UPI Lite. Set each app's accounts in Settings → Auto-detect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PillButton(
                            "Add widget",
                            enabled = app != null,
                            onClick = { save(widgetId, app!!) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    private fun save(widgetId: Int, pkg: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@PayWidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@PayWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { p ->
                p.toMutablePreferences().apply {
                    this[PayWidgetKeys.app] = pkg
                }
            }
            PayWidget().update(this@PayWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
