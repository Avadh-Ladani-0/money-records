package com.moneyrecords.app.ui.feature.records

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.theme.Space

@Composable
fun RecordsScreen(onOpenSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "Records", eyebrow = "All accounts", actions = { SettingsAction(onOpenSettings) }) }
        item {
            EmptyState(
                title = "Nothing recorded",
                message = "Your income, expenses and transfers will appear here, grouped by day.",
            )
        }
    }
}
