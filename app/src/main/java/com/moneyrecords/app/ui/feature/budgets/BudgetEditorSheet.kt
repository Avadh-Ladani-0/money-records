package com.moneyrecords.app.ui.feature.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.ui.components.SegmentedPills
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.icons.AppIcons
import java.time.ZoneId
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.icons.CategoryIcon
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Add or edit a budget for the viewed month (it applies from that month onwards).
 * Adding: choose Overall, Everything else, or a main expense category, then the amount.
 * Editing: change the amount, or stop the budget from this month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorSheet(
    state: BudgetsState,
    initialTarget: BudgetTarget?,
    existing: BudgetProgress?,
    viewModel: BudgetsViewModel,
    onDismiss: () -> Unit,
    /** Called after a successful save (e.g. to ask for notification permission). */
    onSaved: () -> Unit = {},
) {
    val monthName = remember(state.month) { state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) }
    var target by remember { mutableStateOf(existing?.budget?.target ?: initialTarget) }
    // The full list of targets is shown only while choosing; once picked it collapses to one row.
    var choosing by remember { mutableStateOf(existing == null && initialTarget == null) }
    var accountsTab by remember { mutableStateOf(target?.isAccountBased == true) }
    var amount by remember {
        mutableStateOf(existing?.budget?.limit?.let { Money.format(it, symbol = "", sign = Money.Sign.NONE) } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    val budgeted = state.budgetedTargets
    val uncoveredCategories = state.mainCategories.count { BudgetTarget.category(it.id) !in budgeted }
    val uncoveredAccounts = state.accounts.count { BudgetTarget.account(it.id) !in budgeted }
    val lastMonth by produceState<Long?>(null, target) { value = target?.let { viewModel.lastMonthSpent(it) } }

    // Editing only: records that can be counted back this month, and which are selected.
    var candidates by remember { mutableStateOf<List<RecordDetails>>(emptyList()) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(existing) {
        if (existing != null) {
            candidates = viewModel.offsetCandidates(existing.budget.target)
            picked = viewModel.selectedOffsets(existing.budget.target)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .padding(horizontal = Space.screen)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(if (existing == null) "New budget" else "Edit budget", style = MaterialTheme.typography.headlineSmall)

            fun pick(t: BudgetTarget) { target = t; choosing = false }
            if (existing == null && choosing) {
                SegmentedPills(listOf(false, true), accountsTab, { if (it) "Accounts" else "Categories" }, { accountsTab = it })
                if (!accountsTab) {
                    TargetRow(
                        title = "Overall", subtitle = "All expenses",
                        leading = { Icon(Icons.Outlined.PieChart, contentDescription = null) },
                        selected = target == BudgetTarget.Overall, taken = BudgetTarget.Overall in budgeted,
                        onClick = { pick(BudgetTarget.Overall) },
                    )
                    TargetRow(
                        title = "Everything else",
                        subtitle = countLabel(uncoveredCategories, "category", "categories") + " without their own budget",
                        leading = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
                        selected = target == BudgetTarget.Rest, taken = BudgetTarget.Rest in budgeted,
                        onClick = { pick(BudgetTarget.Rest) },
                    )
                    state.mainCategories.forEach { c ->
                        val t = BudgetTarget.category(c.id)
                        TargetRow(
                            title = c.name, subtitle = null,
                            leading = { CategoryIcon(c.iconKey) },
                            selected = target == t, taken = t in budgeted,
                            onClick = { pick(t) },
                        )
                    }
                } else {
                    if (state.accounts.isEmpty()) {
                        Text("Add an account first (Accounts tab).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.accounts.forEach { a ->
                        val t = BudgetTarget.account(a.id)
                        TargetRow(
                            title = a.name, subtitle = "Expenses + transfers out",
                            leading = { Icon(AppIcons.forKey(a.iconKey), contentDescription = null) },
                            selected = target == t, taken = t in budgeted,
                            onClick = { pick(t) },
                        )
                    }
                    TargetRow(
                        title = "Other accounts",
                        subtitle = countLabel(uncoveredAccounts, "account", "accounts") + " without their own budget",
                        leading = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
                        selected = target == BudgetTarget.OtherAccounts, taken = BudgetTarget.OtherAccounts in budgeted,
                        onClick = { pick(BudgetTarget.OtherAccounts) },
                    )
                }
            } else if (existing == null) {
                // Chosen target, collapsed so the amount field is right below it.
                val t = target
                val category = t?.categoryId?.let { id -> state.mainCategories.firstOrNull { it.id == id } }
                val account = t?.accountId?.let { id -> state.accounts.firstOrNull { it.id == id } }
                ListRow(
                    title = when (t?.scope) {
                        BudgetScope.OVERALL -> "Overall"
                        BudgetScope.REST -> "Everything else"
                        BudgetScope.OTHER_ACCOUNTS -> "Other accounts"
                        BudgetScope.ACCOUNT -> account?.name ?: "Account"
                        else -> category?.name ?: "Category"
                    },
                    subtitle = when (t?.scope) {
                        BudgetScope.OVERALL -> "All expenses"
                        BudgetScope.REST -> countLabel(uncoveredCategories, "category", "categories") + " without their own budget"
                        BudgetScope.OTHER_ACCOUNTS -> countLabel(uncoveredAccounts, "account", "accounts") + " without their own budget"
                        BudgetScope.ACCOUNT -> "Expenses + transfers out"
                        else -> null
                    },
                    leading = {
                        when (t?.scope) {
                            BudgetScope.CATEGORY -> CategoryIcon(category?.iconKey)
                            BudgetScope.ACCOUNT -> Icon(AppIcons.forKey(account?.iconKey), contentDescription = null)
                            BudgetScope.REST, BudgetScope.OTHER_ACCOUNTS -> Icon(Icons.Outlined.MoreHoriz, contentDescription = null)
                            else -> Icon(Icons.Outlined.PieChart, contentDescription = null)
                        }
                    },
                    trailing = { TextButton(onClick = { choosing = true }) { Text("Change", color = MaterialTheme.colorScheme.onBackground) } },
                )
            } else {
                ListRow(
                    title = existing.label(),
                    subtitle = when (existing.budget.scope) {
                        BudgetScope.REST -> countLabel(existing.coveredCount, "category", "categories") + " without their own budget"
                        BudgetScope.OTHER_ACCOUNTS -> countLabel(existing.coveredCount, "account", "accounts") + " without their own budget"
                        BudgetScope.ACCOUNT -> "Expenses + transfers out"
                        else -> null
                    },
                    leading = { BudgetIcon(existing, size = 22.dp) },
                )
            }

            MrTextField(
                value = amount,
                onValueChange = { amount = it; error = null },
                label = "Monthly limit",
                prefix = "₹ ",
                keyboardType = KeyboardType.Decimal,
                isError = amount.isNotBlank() && Money.parse(amount) == null,
                supportingText = lastMonth?.let { "Last month you spent ${Money.format(it)}" },
            )
            Text(
                "Applies from $monthName onwards, until you change it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (existing != null) {
                CountBackSection(
                    candidates = candidates,
                    picked = picked,
                    accountBased = existing.budget.target.isAccountBased,
                    onToggle = { id -> picked = if (id in picked) picked - id else picked + id },
                )
            }
            error?.let { Text(it, color = MrTheme.colors.accent, style = MaterialTheme.typography.bodyMedium) }

            PillButton(
                text = "Save",
                enabled = target != null && Money.parse(amount) != null,
                onClick = {
                    val t = target ?: return@PillButton
                    viewModel.save(t, Money.parse(amount), offsets = if (existing != null) picked else null) { e ->
                        if (e == null) { onSaved(); onDismiss() } else error = e
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (existing != null) {
                GhostButton(
                    text = "Stop this budget from $monthName",
                    onClick = { viewModel.stop(existing.budget.target); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

/**
 * Hand-picked records that reduce this budget's spent this month: income (any budget) and, for
 * account budgets, transfers into the account.
 */
@Composable
private fun CountBackSection(
    candidates: List<RecordDetails>,
    picked: Set<Long>,
    accountBased: Boolean,
    onToggle: (Long) -> Unit,
) {
    val total = candidates.filter { it.record.id in picked }.sumOf { it.record.amount }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Count back this month", Modifier.weight(1f))
            if (total > 0) Text("− ${Money.format(total)}", style = MaterialTheme.typography.labelLarge, color = MrTheme.colors.positive)
        }
        Text(
            if (accountBased) "Selected income or transfers into this account reduce what's counted as spent."
            else "Selected income (e.g. a refund or reimbursement) reduces what's counted as spent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (candidates.isEmpty()) {
            Text(
                if (accountBased) "No income or transfers in this month." else "No income this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        candidates.forEach { d ->
            val r = d.record
            val isTransfer = r.kind == RecordKind.TRANSFER
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .toggleable(value = r.id in picked, role = Role.Checkbox, onValueChange = { onToggle(r.id) })
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = r.id in picked, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = Space.sm)) {
                    Text(
                        if (isTransfer) "Transfer from ${d.accountName}" else (d.categoryName ?: "Income"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val sub = listOfNotNull(
                        r.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate().friendly(),
                        if (isTransfer) null else d.accountName,
                        r.note.takeIf { it.isNotBlank() },
                    )
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(r.amount), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun TargetRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    selected: Boolean,
    taken: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = leading,
        onClick = if (taken) null else onClick,
        trailing = when {
            taken -> ({ Tag("Has budget") })
            selected -> ({ Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MrTheme.colors.accent) })
            else -> null
        },
    )
}
