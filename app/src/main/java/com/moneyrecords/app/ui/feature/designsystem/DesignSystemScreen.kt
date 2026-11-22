package com.moneyrecords.app.ui.feature.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.DotProgress
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.RecordRow
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/** A living style guide: every brand token and component with sample values. */
@Composable
fun DesignSystemScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val brand = MrTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { ScreenHeader(title = "Design system", eyebrow = "MoneyRecords", onBack = onBack) }

        item { SectionLabel("Colour") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Swatch(scheme.background, "bg")
                Swatch(scheme.surfaceContainer, "card")
                Swatch(scheme.onSurfaceVariant, "muted")
                Swatch(scheme.onBackground, "ink")
                Swatch(brand.accent, "accent")
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Type") }
        item { Text("1,24,500", style = MaterialTheme.typography.displayLarge) }
        item { Text("Display · Doto", style = MaterialTheme.typography.displaySmall) }
        item { Text("Headline · Inter", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Title · Inter semibold", style = MaterialTheme.typography.titleMedium) }
        item { Text("Body text for notes and descriptions.", style = MaterialTheme.typography.bodyLarge) }
        item { SectionLabel("Label · uppercase tracked") }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Hero amount") }
        item {
            MrCard {
                Column(Modifier.dotGrid(brand.dotGrid)) {
                    SectionLabel("Total balance")
                    HeroAmount(paise = 12_45_678_50L)
                    Spacer(Modifier.height(Space.md))
                    HeroAmount(paise = -3_250_00L, style = MaterialTheme.typography.displaySmall, color = brand.negative)
                }
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Dot progress") }
        item { Labeled("Groceries · 30%") { DotProgress(0.30f) } }
        item { Labeled("Fuel · 78%") { DotProgress(0.78f) } }
        item { Labeled("Dining · 120% — over") { DotProgress(1.2f) } }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Record rows") }
        item { RecordRow("Salary", "Bank · 1 Nov", 85_000_00L, isIncome = true, icon = Icons.Outlined.Payments) }
        item { RecordRow("Coffee", "Cash · Today", 180_00L, isIncome = false, icon = Icons.Outlined.LocalCafe) }
        item { RecordRow("Groceries", "Card · Yesterday", 2_349_50L, isIncome = false, icon = Icons.Outlined.ShoppingBag) }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Inline amounts & tags") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                AmountText(1_00_000_00L)
                AmountText(-450_75L, color = brand.negative)
                Tag("Soon")
                Tag("Over", color = brand.accent)
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Buttons") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PillButton("Save", onClick = {})
                GhostButton("Cancel", onClick = {})
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Empty state") }
        item { EmptyState(title = "No records yet", message = "Tap + to add your first income or expense.") }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(1.dp, MrTheme.colors.hairline),
        ) { Box(Modifier) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}
