package com.moneyrecords.app.ui.feature.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
) {
    val monthName = remember(state.month) { state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) }
    var target by remember { mutableStateOf(existing?.budget?.target ?: initialTarget) }
    // The full list of targets is shown only while choosing; once picked it collapses to one row.
    var choosing by remember { mutableStateOf(existing == null && initialTarget == null) }
    var amount by remember {
        mutableStateOf(existing?.budget?.limit?.let { Money.format(it, symbol = "", sign = Money.Sign.NONE) } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    val budgeted = state.budgetedTargets
    val uncovered = state.mainCategories.count { BudgetTarget.category(it.id) !in budgeted }
    val lastMonth by produceState<Long?>(null, target) { value = target?.let { viewModel.lastMonthSpent(it) } }

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
                SectionLabel("Budget for")
                TargetRow(
                    title = "Overall", subtitle = "All expenses",
                    leading = { Icon(Icons.Outlined.PieChart, contentDescription = null) },
                    selected = target == BudgetTarget.Overall, taken = BudgetTarget.Overall in budgeted,
                    onClick = { pick(BudgetTarget.Overall) },
                )
                TargetRow(
                    title = "Everything else",
                    subtitle = "$uncovered categories without their own budget",
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
            } else if (existing == null) {
                // Chosen target, collapsed so the amount field is right below it.
                val t = target
                val category = t?.categoryId?.let { id -> state.mainCategories.firstOrNull { it.id == id } }
                ListRow(
                    title = when (t?.scope) {
                        BudgetScope.OVERALL -> "Overall"
                        BudgetScope.REST -> "Everything else"
                        else -> category?.name ?: "Category"
                    },
                    subtitle = when (t?.scope) {
                        BudgetScope.OVERALL -> "All expenses"
                        BudgetScope.REST -> "$uncovered categories without their own budget"
                        else -> null
                    },
                    leading = {
                        when (t?.scope) {
                            BudgetScope.CATEGORY -> CategoryIcon(category?.iconKey)
                            BudgetScope.REST -> Icon(Icons.Outlined.MoreHoriz, contentDescription = null)
                            else -> Icon(Icons.Outlined.PieChart, contentDescription = null)
                        }
                    },
                    trailing = { TextButton(onClick = { choosing = true }) { Text("Change", color = MaterialTheme.colorScheme.onBackground) } },
                )
            } else {
                ListRow(
                    title = existing.label(),
                    subtitle = if (existing.budget.scope == BudgetScope.REST) "${existing.coveredCount} categories without their own budget" else null,
                    leading = {
                        if (existing.budget.scope == BudgetScope.CATEGORY) CategoryIcon(existing.categoryIconKey)
                        else Icon(if (existing.budget.scope == BudgetScope.REST) Icons.Outlined.MoreHoriz else Icons.Outlined.PieChart, contentDescription = null)
                    },
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
            error?.let { Text(it, color = MrTheme.colors.accent, style = MaterialTheme.typography.bodyMedium) }

            PillButton(
                text = "Save",
                enabled = target != null && Money.parse(amount) != null,
                onClick = {
                    val t = target ?: return@PillButton
                    viewModel.save(t, Money.parse(amount)) { e -> if (e == null) onDismiss() else error = e }
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
