package com.moneyrecords.app.ui.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.theme.Space

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val calculatorEnabled by viewModel.calculatorEnabled.collectAsStateWithLifecycle()
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
            ListRow(title = "Appearance", subtitle = "Follows system", icon = Icons.Outlined.DarkMode, trailing = { Tag("Soon") })
        }
        item {
            ListRow(title = "Categories", subtitle = "Add, rename, nest or archive", icon = Icons.Outlined.Category, onClick = onOpenCategories)
        }
        item {
            ListRow(
                title = "Floating calculator",
                subtitle = "Quick calculator bubble on every screen",
                icon = Icons.Outlined.Calculate,
                onClick = { viewModel.setCalculatorEnabled(!calculatorEnabled) },
                trailing = {
                    Switch(
                        checked = calculatorEnabled,
                        onCheckedChange = viewModel::setCalculatorEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = MrTheme.colors.accent),
                    )
                },
            )
        }

        item { Spacer(Modifier.height(Space.xl)); SectionLabel("Security & data") }
        item {
            ListRow(title = "App lock", subtitle = "PIN and fingerprint", icon = Icons.Outlined.Lock, trailing = { Tag("Soon") })
        }
        item {
            ListRow(title = "Backup & restore", subtitle = "Save your data to a file", icon = Icons.Outlined.SettingsBackupRestore, trailing = { Tag("Soon") })
        }

        item { Spacer(Modifier.height(Space.xl)); SectionLabel("About") }
        item {
            ListRow(title = "Design system", subtitle = "Preview colours, type and components", icon = Icons.Outlined.GridView, onClick = onOpenDesignSystem)
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
