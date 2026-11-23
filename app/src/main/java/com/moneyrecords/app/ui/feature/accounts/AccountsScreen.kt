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
import com.moneyrecords.app.domain.money.Money
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Groups
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

@Composable
fun AccountsScreen(
    onOpenSettings: () -> Unit,
    onOpenToCollect: () -> Unit,
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
                subtitle = "Cash, bank & cards",
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

        // Home already shows the big balance; here one compact line, and the accounts are the hero.
        if (!state.loading) item(key = "summary") {
            Summary(state)
            Spacer(Modifier.height(Space.lg))
        }
        if (state.toCollect > 0) item(key = "to-collect") {
            ListRow(
                title = "To collect",
                subtitle = "Owed to you for bills you split",
                icon = Icons.Outlined.Groups,
                onClick = onOpenToCollect,
                trailing = { AmountText(state.toCollect, animate = true) },
            )
            Spacer(Modifier.height(Space.sm))
        }

        items(state.active, key = { it.id }) { account ->
            AccountRow(account, state.flows[account.id], onClick = { onOpenAccount(account.id) }, modifier = Modifier.animateItem())
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
                    AccountRow(account, null, onClick = { onOpenAccount(account.id) }, modifier = Modifier.animateItem())
                }
            }
        }
        item { Spacer(Modifier.height(Space.fabClearance)) }
    }
}

@Composable
private fun Summary(state: AccountsState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        SummaryFigure("Assets", state.assets, MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
        SummaryFigure("Owed", state.owed, if (state.owed > 0) MrTheme.colors.expense else MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
        SummaryFigure("Net", state.total, if (state.total < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryFigure(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        SectionLabel(label)
        AmountText(paise, color = color, animate = true)
    }
}

@Composable
private fun AccountRow(account: Account, flow: AccountFlow?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val monthLine = flow?.let { "In ${Money.format(it.inflow)} · Out ${Money.format(it.outflow)} this month" }
    ListRow(
        title = account.name,
        subtitle = listOfNotNull(account.type.label(), "Archived".takeIf { account.archived }, monthLine).joinToString(" · "),
        subtitleLines = 2,
        icon = AppIcons.forKey(account.iconKey),
        onClick = onClick,
        modifier = modifier,
        spokenText = buildString {
            append("${account.name}, ${account.type.label()}, balance ${Money.spoken(account.balance)}")
            flow?.let { append(", in ${Money.spoken(it.inflow)}, out ${Money.spoken(it.outflow)} this month") }
        },
        trailing = {
            AmountText(
                paise = account.balance,
                color = if (account.balance < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
                animate = true,
            )
        },
    )
}
