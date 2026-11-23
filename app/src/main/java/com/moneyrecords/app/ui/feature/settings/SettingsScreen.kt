package com.moneyrecords.app.ui.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.ui.theme.MrTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneyrecords.app.BuildConfig
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SwitchRow
import com.moneyrecords.app.ui.theme.Space

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAutoDetect: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val calculatorEnabled by viewModel.calculatorEnabled.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "Settings", onBack = onBack) }

        item { SectionLabel("General") }
        item {
            ListRow(title = "Currency", subtitle = "Indian rupee", icon = Icons.Outlined.CurrencyRupee, trailing = { Value("₹ INR") })
        }
        item {
            ListRow(title = "Appearance", subtitle = "${theme.label} theme", icon = Icons.Outlined.Palette, onClick = onOpenAppearance)
        }
        item {
            ListRow(title = "Categories", subtitle = "Add, rename, nest or archive", icon = Icons.Outlined.Category, onClick = onOpenCategories)
        }
        item {
            ListRow(title = "Recurring", subtitle = "Rent, salary, subscriptions", icon = Icons.Outlined.EventRepeat, onClick = onOpenRecurring)
        }
        item {
            ListRow(title = "Auto-detect payments", subtitle = "Bank SMS and payment apps → one-tap records", icon = Icons.Outlined.AutoAwesome, onClick = onOpenAutoDetect)
        }
        item {
            ListRow(title = "Notifications", subtitle = "Due items, reminders, budget alerts", icon = Icons.Outlined.Notifications, onClick = onOpenNotifications)
        }
        item {
            SwitchRow(
                title = "Floating calculator",
                subtitle = "A slim handle on the right edge of every screen",
                icon = Icons.Outlined.Calculate,
                checked = calculatorEnabled,
                onCheckedChange = viewModel::setCalculatorEnabled,
            )
        }

        item { Spacer(Modifier.height(Space.xl)); SectionLabel("Security & data") }
        item {
            ListRow(title = "App lock", subtitle = "Fingerprint, face or phone screen lock", icon = Icons.Outlined.Lock, onClick = onOpenAppLock)
        }
        item {
            ListRow(title = "Backup & restore", subtitle = "Save your data to a file, or restore it", icon = Icons.Outlined.SettingsBackupRestore, onClick = onOpenBackup)
        }

        item { Spacer(Modifier.height(Space.xl)); SectionLabel("About") }
        // A developer page: only in debug builds.
        if (BuildConfig.DEBUG) {
            item {
                ListRow(title = "Design system", subtitle = "Preview colours, type and components", icon = Icons.Outlined.GridView, onClick = onOpenDesignSystem)
            }
        }
        item {
            ListRow(title = "Version", subtitle = "MoneyRecords", icon = Icons.Outlined.Info, trailing = { Value(BuildConfig.VERSION_NAME) })
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
