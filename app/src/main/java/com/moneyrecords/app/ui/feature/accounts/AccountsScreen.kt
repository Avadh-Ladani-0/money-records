package com.moneyrecords.app.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

@Composable
fun AccountsScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item {
            ScreenHeader(
                title = "Accounts",
                eyebrow = "Cash, bank & cards",
                actions = {
                    IconButton(onClick = onAddAccount) { Icon(Icons.Outlined.Add, contentDescription = "Add account") }
                    SettingsAction(onOpenSettings)
                },
            )
        }

        if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
            item {
                EmptyState(
                    title = "No accounts yet",
                    message = "Add the places your money lives — Cash, a bank account, a card or a wallet.",
                    action = { PillButton("Add account", onClick = onAddAccount) },
                )
            }
            return@LazyColumn
        }

        item {
            MrCard {
                Column(Modifier.dotGrid(MrTheme.colors.dotGrid)) {
                    SectionLabel("Total across accounts")
                    Spacer(Modifier.height(Space.sm))
                    HeroAmount(
                        paise = state.total,
                        style = MaterialTheme.typography.displayMedium,
                        color = if (state.total < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(Space.xl))
        }

        items(state.active, key = { it.id }) { account ->
            AccountRow(account, onClick = { onOpenAccount(account.id) })
        }

        if (state.archived.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.lg))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionLabel("Archived · ${state.archived.size}")
                    IconButton(onClick = { showArchived = !showArchived }) {
                        Icon(
                            if (showArchived) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (showArchived) "Hide archived" else "Show archived",
                        )
                    }
                }
            }
            if (showArchived) {
                items(state.archived, key = { it.id }) { account ->
                    AccountRow(account, onClick = { onOpenAccount(account.id) })
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    ListRow(
        title = account.name,
        subtitle = account.type.label() + if (account.archived) " · Archived" else "",
        icon = AppIcons.forKey(account.iconKey),
        onClick = onClick,
        trailing = {
            AmountText(
                paise = account.balance,
                color = if (account.balance < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
            )
        },
    )
}
