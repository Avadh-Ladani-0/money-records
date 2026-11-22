package com.moneyrecords.app.ui.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.DotProgress
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.icons.CategoryBadge
import com.moneyrecords.app.ui.theme.AppTheme
import com.moneyrecords.app.ui.theme.MoneyRecordsTheme
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/**
 * Settings → Appearance: pick Nothing or Graphite. Each card is drawn in its own theme with real
 * components, so what you see is what you get. Switching is instant.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    LazyColumn(
        modifier = Modifier.fillMaxSize().selectableGroup(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { ScreenHeader(title = "Appearance", onBack = onBack) }
        AppTheme.entries.forEach { theme ->
            item(key = theme.name) {
                ThemeCard(theme, selected = theme == current, onSelect = {
                    if (theme != current) haptics.select()
                    viewModel.setTheme(theme)
                })
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: AppTheme, selected: Boolean, onSelect: () -> Unit) {
    val outerBorder by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onBackground else MrTheme.colors.hairline,
        label = "themeBorder",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(if (selected) 2.dp else 1.dp, outerBorder),
            color = MaterialTheme.colorScheme.background,
        ) {
            // The preview is rendered in the theme it shows.
            MoneyRecordsTheme(theme) { ThemePreview() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Space.sm)) {
            Column(Modifier.weight(1f)) {
                Text(theme.label, style = MaterialTheme.typography.titleMedium)
                Text(theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** A small slice of Home: balance, two records and a budget bar. Sample figures only. */
@Composable
private fun ThemePreview() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        HeroAmount(48_250_00, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        PreviewRow("food", "Food & Dining", -1_323_00)
        PreviewRow("salary", "Salary", 50_000_00)
        Text("Food budget · 82% used", style = MaterialTheme.typography.bodySmall, color = MrTheme.colors.warning)
        DotProgress(progress = 0.82f, dotSize = 6.dp, activeColor = MrTheme.colors.warning)
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun PreviewRow(iconKey: String, name: String, paise: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(iconKey, size = 36.dp, iconSize = 18.dp)
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = Space.md))
        AmountText(
            paise,
            sign = Money.Sign.ALWAYS,
            style = MaterialTheme.typography.titleSmall,
            color = if (paise < 0) MrTheme.colors.expense else MrTheme.colors.positive,
        )
    }
}
