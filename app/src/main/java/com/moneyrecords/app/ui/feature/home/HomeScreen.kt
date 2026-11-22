package com.moneyrecords.app.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
    val month = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "MoneyRecords", eyebrow = today, actions = { SettingsAction(onOpenSettings) }) }

        item {
            MrCard(modifier = Modifier) {
                Column(Modifier.dotGrid(MrTheme.colors.dotGrid)) {
                    SectionLabel("Total balance")
                    Spacer(Modifier.height(Space.sm))
                    HeroAmount(paise = 0L)
                    Spacer(Modifier.height(Space.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("In · $month")
                            AmountText(0L, color = MrTheme.colors.positive)
                        }
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Out · $month")
                            AmountText(0L, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(Space.xxl))
            SectionLabel("Recent records")
        }
        item {
            EmptyState(
                title = "No records yet",
                message = "Tap + to add your first income or expense.",
            )
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
