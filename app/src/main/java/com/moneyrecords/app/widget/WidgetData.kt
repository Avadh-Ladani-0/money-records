package com.moneyrecords.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.TimeRange
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.ui.theme.AppTheme
import com.moneyrecords.app.ui.theme.mrColorsFor
import com.moneyrecords.app.ui.theme.schemeFor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Widgets aren't Hilt components; they reach the repositories through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun accounts(): AccountRepository
    fun records(): RecordRepository
    fun budgets(): BudgetRepository
    fun prefs(): UserPrefsRepository
    fun splits(): com.moneyrecords.app.domain.repository.SplitRepository
    fun clock(): Clock
}

/** One budget line as a widget shows it. */
data class WidgetBudget(
    val name: String,
    val spent: Long,
    val limit: Long,
    val fraction: Float,
    val isOver: Boolean,
    val isNearLimit: Boolean,
)

/** Everything a widget draws, read once per update. */
data class WidgetSnapshot(
    /** App lock is on: amounts are hidden (the widget sits outside the lock). */
    val masked: Boolean,
    val theme: AppTheme,
    val balance: Long,
    val monthName: String,
    val income: Long,
    val expense: Long,
    val todaySpent: Long,
    val overall: WidgetBudget?,
    val budgets: List<WidgetBudget>,
    /** Owed to you by friends. */
    val toCollect: Long = 0,
) {
    val net: Long get() = income - expense

    companion object {
        suspend fun load(context: Context): WidgetSnapshot {
            val e = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val clock = e.clock()
            val today = LocalDate.now(clock)
            val month = YearMonth.from(today)
            val zone = clock.zone
            val dayRange = TimeRange(
                today.atStartOfDay(zone).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            val summary = e.records().observeMonthSummary(month).first()
            val overview = e.budgets().observeOverview(month).first()
            val themeKey = e.prefs().observeTheme().first()
            return WidgetSnapshot(
                masked = e.prefs().observeAppLock().first().enabled,
                theme = AppTheme.entries.firstOrNull { it.name == themeKey } ?: AppTheme.NOTHING,
                balance = e.accounts().observeAccounts().first().sumOf { it.balance },
                monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                income = summary.income,
                expense = summary.expense,
                todaySpent = e.records().observeRecords(RecordFilter(kind = RecordKind.EXPENSE, range = dayRange)).first()
                    .sumOf { it.record.amount },
                overall = overview.overall?.toWidget(),
                budgets = overview.all.map { it.toWidget() },
                toCollect = e.splits().observeOpen().first().sumOf { it.outstanding },
            )
        }

        private fun BudgetProgress.toWidget() = WidgetBudget(
            name = when (budget.scope) {
                BudgetScope.OVERALL -> "Overall"
                BudgetScope.REST -> "Everything else"
                BudgetScope.CATEGORY -> categoryName ?: "Category"
                BudgetScope.ACCOUNT -> accountName ?: "Account"
                BudgetScope.OTHER_ACCOUNTS -> "Other accounts"
            },
            spent = spent,
            limit = budget.limit,
            fraction = fraction,
            isOver = isOver,
            isNearLimit = isNearLimit,
        )
    }
}

/** The app theme's colours, flattened for Glance. */
data class WidgetPalette(
    val background: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val expense: Color,
    val negative: Color,
    val warning: Color,
    val over: Color,
) {
    companion object {
        fun of(theme: AppTheme): WidgetPalette {
            val s = schemeFor(theme)
            val m = mrColorsFor(theme)
            return WidgetPalette(
                background = s.background,
                card = s.surfaceContainerHigh,
                ink = s.onBackground,
                muted = s.onSurfaceVariant,
                dim = m.dotDim,
                accent = m.accent,
                onAccent = m.onAccent,
                positive = m.positive,
                expense = m.expense,
                negative = m.negative,
                warning = m.warning,
                over = m.over,
            )
        }
    }
}
