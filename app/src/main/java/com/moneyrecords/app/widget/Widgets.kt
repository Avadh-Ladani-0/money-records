package com.moneyrecords.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.notify.Notifications

// ── Quick add: − Expense · + Income · ⇄ Transfer, and today's spend ─────────────────────────────

class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshot.load(context)
        provideContent { QuickAdd(snap) }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickAdd(s: WidgetSnapshot) {
    val c = WidgetPalette.of(s.theme)
    val context = LocalContext.current
    val wide = LocalSize.current.width >= 250.dp
    Row(
        GlanceModifier.fillMaxSize().background(c.background.glance()).cornerRadius(24.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Column(
                GlanceModifier.padding(horizontal = 10.dp).clickable(actionStartActivity(appIntent(context, Notifications.ACTION_OPEN_HOME))),
            ) {
                WText("Today", c.muted, 11.sp)
                WText(amount(s.todaySpent, s.masked), c.ink, 16.sp, bold = true)
            }
        }
        listOf(
            Triple("−", "Expense", RecordKind.EXPENSE),
            Triple("+", "Income", RecordKind.INCOME),
            Triple("⇄", "Transfer", RecordKind.TRANSFER),
        ).forEach { (symbol, label, kind) ->
            val primary = kind == RecordKind.EXPENSE
            Box(
                GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(4.dp)
                    .semantics { contentDescription = "Add $label" }
                    .clickable(actionStartActivity(appIntent(context, Notifications.ACTION_ADD_RECORD, kind.name))),
                contentAlignment = Alignment.Center,
            ) {
                Card(if (primary) c.accent else c.card, GlanceModifier.fillMaxSize()) {
                    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        WText(symbol, if (primary) c.onAccent else c.ink, 20.sp, bold = true)
                        WText(label, if (primary) c.onAccent else c.muted, 11.sp)
                    }
                }
            }
        }
    }
}

// ── Month summary: balance, In / Out / Net, overall budget ──────────────────────────────────────

class MonthSummaryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshot.load(context)
        provideContent { MonthSummary(snap) }
    }
}

class MonthSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MonthSummaryWidget()
}

@Composable
private fun MonthSummary(s: WidgetSnapshot) {
    val c = WidgetPalette.of(s.theme)
    val context = LocalContext.current
    val tall = LocalSize.current.height >= 150.dp
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(c.background.glance())
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(appIntent(context, Notifications.ACTION_OPEN_HOME))),
    ) {
        WText("Total balance", c.muted, 11.sp)
        if (s.masked) {
            WText("₹ ••••", c.ink, 28.sp, bold = true)
        } else {
            DotAmount(
                context,
                Money.format(s.balance),
                if (s.balance < 0) c.negative else c.ink,
                height = if (tall) 34.dp else 26.dp,
                description = "Total balance ${Money.spoken(s.balance)}",
            )
        }
        Spacer(8.dp)
        Row(GlanceModifier.fillMaxWidth()) {
            Figure("In · ${s.monthName}", amount(s.income, s.masked), c.positive, c, GlanceModifier.defaultWeight())
            Figure("Out", amount(s.expense, s.masked), c.expense, c, GlanceModifier.defaultWeight())
            Figure("Net", amount(s.net, s.masked, Money.Sign.ALWAYS), if (s.net >= 0) c.positive else c.expense, c, GlanceModifier.defaultWeight())
        }
        val overall = s.overall
        if (tall && overall != null) {
            Spacer(10.dp)
            Row(GlanceModifier.fillMaxWidth()) {
                WText("Budget", c.muted, 11.sp, modifier = GlanceModifier.defaultWeight())
                WText(
                    if (s.masked) "${(overall.fraction * 100).toInt()}% used"
                    else "${Money.format(overall.spent)} of ${Money.format(overall.limit)}",
                    barColor(overall, c),
                    11.sp,
                )
            }
            Spacer(4.dp)
            WBar(overall.fraction, barColor(overall, c), c.dim)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color, c: WidgetPalette, modifier: GlanceModifier) {
    Column(modifier) {
        WText(label, c.muted, 11.sp)
        WText(value, color, 14.sp, bold = true)
    }
}

private fun barColor(b: WidgetBudget, c: WidgetPalette) = when {
    b.isOver -> c.over
    b.isNearLimit -> c.warning
    else -> c.ink
}

// ── Budgets: every budget's bar and what's left ─────────────────────────────────────────────────

class BudgetsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshot.load(context)
        provideContent { Budgets(snap) }
    }
}

class BudgetsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BudgetsWidget()
}

@Composable
private fun Budgets(s: WidgetSnapshot) {
    val c = WidgetPalette.of(s.theme)
    val context = LocalContext.current
    val open = actionStartActivity(appIntent(context, Notifications.ACTION_OPEN_BUDGETS))
    Column(
        GlanceModifier.fillMaxSize().background(c.background.glance()).cornerRadius(24.dp).padding(16.dp).clickable(open),
    ) {
        WText("Budgets · ${s.monthName}", c.muted, 11.sp)
        Spacer(6.dp)
        if (s.budgets.isEmpty()) {
            WText("No budgets yet — tap to set one.", c.ink, 13.sp, maxLines = 2)
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(s.budgets) { b ->
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(open)) {
                        Row(GlanceModifier.fillMaxWidth()) {
                            WText(b.name, c.ink, 13.sp, modifier = GlanceModifier.defaultWeight())
                            WText(
                                when {
                                    s.masked -> "${(b.fraction * 100).toInt()}%"
                                    b.isOver -> "Over ${Money.format(b.spent - b.limit)}"
                                    else -> "${Money.format(b.limit - b.spent)} left"
                                },
                                if (b.isOver) c.over else if (b.isNearLimit) c.warning else c.muted,
                                12.sp,
                            )
                        }
                        Box(GlanceModifier.height(4.dp).width(1.dp)) {}
                        WBar(b.fraction, barColor(b, c), c.dim)
                    }
                }
            }
        }
    }
}
