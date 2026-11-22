package com.moneyrecords.app.notify

import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject

/** Checks budgets after a record is saved; used by the editor and recurring "Add". */
interface BudgetAlerts {
    suspend fun checkAfterRecord(month: YearMonth)
}

/**
 * Sends "80% used" and "over budget" alerts, each once per budget per month. If a single record jumps
 * straight past 100%, only the "over" alert is sent (and 80% is marked as done too).
 */
class BudgetAlertChecker @Inject constructor(
    private val budgets: BudgetRepository,
    private val prefs: UserPrefsRepository,
    private val poster: BudgetAlertPoster,
) : BudgetAlerts {

    override suspend fun checkAfterRecord(month: YearMonth) {
        if (!prefs.notifications().budgetAlerts) return
        budgets.observeOverview(month).first().all.forEach { p ->
            val threshold = thresholdFor(p) ?: return@forEach
            val key80 = key(p, month, 80)
            val key100 = key(p, month, 100)
            if (threshold == 100) {
                if (prefs.markSent(key100)) {
                    prefs.markSent(key80)
                    poster.post(p, 100)
                }
            } else if (prefs.markSent(key80)) {
                poster.post(p, 80)
            }
        }
    }

    companion object {
        fun thresholdFor(p: BudgetProgress): Int? = when {
            p.isOver -> 100
            p.fraction >= 0.8f -> 80
            else -> null
        }

        fun key(p: BudgetProgress, month: YearMonth, threshold: Int): String {
            val t = p.budget.target
            return "budget:${t.scope}:${t.categoryId ?: "-"}:${t.accountId ?: "-"}:$month:$threshold"
        }
    }
}
