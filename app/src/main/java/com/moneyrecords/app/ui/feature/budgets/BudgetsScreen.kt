package com.moneyrecords.app.ui.feature.budgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import com.moneyrecords.app.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.DotProgress
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.Glyphs
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.icons.CategoryIcon
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the editor sheet is open for: a new budget (optionally pre-picked target) or an existing one. */
private data class EditorRequest(val target: BudgetTarget?, val existing: BudgetProgress?)

@Composable
fun BudgetsScreen(
    onOpenSettings: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val overview = state.overview
    // Ask for notification permission (month-start budget check) the first time a budget is saved.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item {
            ScreenHeader(
                title = "Budgets",
                actions = {
                    IconButton(onClick = { editor = EditorRequest(null, null) }) { Icon(Icons.Outlined.Add, contentDescription = "Add budget") }
                    SettingsAction(onOpenSettings)
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month") }
                Text(state.month.format(monthFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month") }
            }
            Spacer(Modifier.height(Space.sm))
        }

        if (overview != null && overview.isEmpty) {
            item {
                EmptyState(
                    title = "No budgets for ${state.month.format(monthFormat)}",
                    message = "Set a monthly limit. It carries on to the following months until you change it.",
                    glyph = Glyphs.Chart,
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            PillButton("Set an overall budget", onClick = { editor = EditorRequest(BudgetTarget.Overall, null) })
                            GhostButton("Budget a category", onClick = { editor = EditorRequest(null, null) })
                        }
                    },
                )
            }
        }

        overview?.overall?.let { overall ->
            item {
                OverallCard(overall, perDay = state.perDay(overall), onClick = { editor = EditorRequest(overall.budget.target, overall) })
                Spacer(Modifier.height(Space.xl))
            }
        }
        if (overview != null && !overview.isEmpty && overview.overall == null) {
            item {
                TextButton(onClick = { editor = EditorRequest(BudgetTarget.Overall, null) }) {
                    Text("+ Set an overall budget", color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(Space.sm))
            }
        }

        if (overview != null && (overview.categories.isNotEmpty() || overview.rest != null)) {
            item { SectionLabel("Categories"); Spacer(Modifier.height(Space.sm)) }
            items(overview.categories, key = { "c${it.budget.categoryId}" }) { p ->
                BudgetRow(p, perDay = state.perDay(p), onClick = { editor = EditorRequest(p.budget.target, p) })
            }
            overview.rest?.let { rest ->
                item(key = "rest") { BudgetRow(rest, perDay = state.perDay(rest), onClick = { editor = EditorRequest(rest.budget.target, rest) }) }
            }
        }

        if (overview != null && (overview.accounts.isNotEmpty() || overview.otherAccounts != null)) {
            item { Spacer(Modifier.height(Space.lg)); SectionLabel("By account"); Spacer(Modifier.height(Space.sm)) }
            items(overview.accounts, key = { "a${it.budget.accountId}" }) { p ->
                BudgetRow(p, perDay = state.perDay(p), onClick = { editor = EditorRequest(p.budget.target, p) })
            }
            overview.otherAccounts?.let { other ->
                item(key = "otherAccounts") { BudgetRow(other, perDay = state.perDay(other), onClick = { editor = EditorRequest(other.budget.target, other) }) }
            }
        }

        if (overview != null && overview.suggestions.isNotEmpty()) {
            item { Spacer(Modifier.height(Space.xl)); SectionLabel("Spending without a budget") }
            items(overview.suggestions, key = { "s${it.categoryId}" }) { s ->
                ListRow(
                    title = s.name,
                    subtitle = Money.format(s.spent) + " spent this month",
                    leading = { CategoryIcon(s.iconKey) },
                    onClick = { editor = EditorRequest(BudgetTarget.category(s.categoryId), null) },
                    trailing = { Text("Add", style = MaterialTheme.typography.labelLarge, color = MrTheme.colors.accent) },
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    editor?.let { request ->
        BudgetEditorSheet(
            state = state,
            initialTarget = request.target,
            existing = request.existing,
            viewModel = viewModel,
            onDismiss = { editor = null },
            onSaved = askForNotifications,
        )
    }
}

fun BudgetProgress.label(): String = when (budget.scope) {
    BudgetScope.OVERALL -> "Overall"
    BudgetScope.REST -> "Everything else"
    BudgetScope.CATEGORY -> categoryName ?: "Category"
    BudgetScope.ACCOUNT -> accountName ?: "Account"
    BudgetScope.OTHER_ACCOUNTS -> "Other accounts"
}

/** Round-badge content for a budget line. */
@Composable
fun BudgetIcon(p: BudgetProgress, size: androidx.compose.ui.unit.Dp = 20.dp) {
    when (p.budget.scope) {
        BudgetScope.CATEGORY -> CategoryIcon(p.categoryIconKey, size = size)
        BudgetScope.ACCOUNT -> Icon(AppIcons.forKey(p.accountIconKey), contentDescription = null, modifier = Modifier.size(size))
        BudgetScope.OVERALL -> Icon(Icons.Outlined.PieChart, contentDescription = null, modifier = Modifier.size(size))
        BudgetScope.REST, BudgetScope.OTHER_ACCOUNTS -> Icon(Icons.Outlined.MoreHoriz, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun StatusTag(p: BudgetProgress) {
    when {
        p.isOver -> Tag("Over", color = MrTheme.colors.accent)
        p.isNearLimit -> Tag("${(p.fraction * 100).toInt()}% used", color = MrTheme.colors.accent)
    }
}

@Composable
private fun OverallCard(p: BudgetProgress, perDay: Long?, onClick: () -> Unit) {
    MrCard(onClick = onClick) {
        Column(Modifier.dotGrid(MrTheme.colors.dotGrid)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (p.isOver) "Over budget by" else "Left to spend", Modifier.weight(1f))
                StatusTag(p)
            }
            Spacer(Modifier.height(Space.sm))
            HeroAmount(
                paise = if (p.isOver) -p.remaining else p.remaining,
                style = MaterialTheme.typography.displayMedium,
                color = if (p.isOver) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Space.lg))
            DotProgress(p.fraction, dotSize = 10.dp)
            Spacer(Modifier.height(Space.md))
            Row {
                Text(
                    "${Money.format(p.spent)} of ${Money.format(p.budget.limit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (perDay != null) {
                    Text(
                        "${Money.format(perDay)} a day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(p: BudgetProgress, perDay: Long?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Space.md, horizontal = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BudgetIcon(p)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(p.label(), style = MaterialTheme.typography.bodyLarge)
                    StatusTag(p)
                }
                val sub = buildList {
                    if (p.budget.scope == BudgetScope.REST) add(countLabel(p.coveredCount, "category", "categories"))
                    if (p.budget.scope == BudgetScope.OTHER_ACCOUNTS) add(countLabel(p.coveredCount, "account", "accounts"))
                    add(if (p.isOver) "${Money.format(-p.remaining)} over" else "${Money.format(p.remaining)} left")
                    if (perDay != null && !p.isOver) add("${Money.format(perDay)}/day")
                    if (p.offset > 0) add("${Money.format(p.offset)} counted back")
                }
                Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(p.spent), style = MaterialTheme.typography.titleSmall,
                    color = if (p.isOver) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground)
                Text("of ${Money.format(p.budget.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Space.sm))
        DotProgress(p.fraction, dotSize = 6.dp)
    }
}

/** "1 category", "3 categories"; "1 account", "2 accounts". */
fun countLabel(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
