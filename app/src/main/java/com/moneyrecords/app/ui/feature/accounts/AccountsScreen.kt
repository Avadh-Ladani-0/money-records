package com.moneyrecords.app.ui.feature.accounts

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
fun AccountsScreen(onOpenSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item {
            ScreenHeader(title = "Accounts", eyebrow = "Cash, bank & cards", actions = { SettingsAction(onOpenSettings) })
        }
        item {
            EmptyState(
                title = "No accounts yet",
                message = "Add the places your money lives — Cash, a bank account, a card or a wallet.",
            )
        }
    }
}
