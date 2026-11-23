package com.moneyrecords.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widgets in step with the data: any change to records, accounts, budgets,
 * the theme or App lock refreshes them (debounced), and the date is re-checked each minute so
 * "today" and the month roll over at midnight.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val records: RecordRepository,
    private val budgets: BudgetRepository,
    private val prefs: UserPrefsRepository,
    private val splits: com.moneyrecords.app.domain.repository.SplitRepository,
    private val clock: Clock,
) {
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        val day = flow {
            while (true) {
                emit(LocalDate.now(clock))
                delay(60_000)
            }
        }.distinctUntilChanged()
        scope.launch {
            day.flatMapLatest { today ->
                val month = YearMonth.from(today)
                combine(
                    accounts.observeAccounts(),
                    records.observeMonthSummary(month),
                    budgets.observeOverview(month),
                    prefs.observeTheme(),
                    prefs.observeAppLock(),
                ) { a, s, b, t, l -> listOf(today, a, s, b, t, l.enabled) }
                    .combine(splits.observeOpen()) { parts, open -> parts + open.sumOf { it.outstanding } }
            }
                .debounce(1_000)
                .collect { refreshAll() }
        }
    }

    suspend fun refreshAll() {
        runCatching {
            QuickAddWidget().updateAll(context)
            MonthSummaryWidget().updateAll(context)
            BudgetsWidget().updateAll(context)
            PayWidget().updateAll(context)
        }
    }
}
