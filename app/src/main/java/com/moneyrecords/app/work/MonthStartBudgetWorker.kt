package com.moneyrecords.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyrecords.app.domain.budget.MonthStartPrompt
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.notify.BudgetPromptNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Runs the month-start check: asks once per month whether to keep last month's budgets. */
class MonthStartBudgetCheck @Inject constructor(
    private val budgets: BudgetRepository,
    private val prefs: UserPrefsRepository,
    private val notifier: BudgetPromptNotifier,
    private val clock: Clock,
) {
    /** Returns true when the notification was shown. */
    suspend fun run(): Boolean {
        val now = LocalDateTime.now(clock)
        val month = YearMonth.from(now)
        val hadBudgets = !budgets.observeOverview(month.minusMonths(1)).first().isEmpty
        if (!MonthStartPrompt.shouldPrompt(now, prefs.lastBudgetPrompt(), hadBudgets)) return false
        if (!notifier.canNotify()) return false
        notifier.show(month)
        prefs.setLastBudgetPrompt(month)
        return true
    }
}

@HiltWorker
class MonthStartBudgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val check: MonthStartBudgetCheck,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        check.run()
        return Result.success()
    }

    companion object {
        private const val NAME = "month-start-budget-check"

        /** Checks a few times a day; [MonthStartPrompt] makes sure it only notifies once per month. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthStartBudgetWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
