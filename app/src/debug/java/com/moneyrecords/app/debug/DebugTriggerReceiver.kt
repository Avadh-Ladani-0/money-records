package com.moneyrecords.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.moneyrecords.app.notify.BudgetPromptNotifier
import com.moneyrecords.app.work.MonthStartBudgetCheck
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * Debug-only: `adb shell am broadcast -p com.moneyrecords.app.debug -a com.moneyrecords.app.debug.SHOW_BUDGET_PROMPT`
 * shows the month-start notification now; RUN_MONTH_START_CHECK runs the real check (date rules apply).
 */
@AndroidEntryPoint
class DebugTriggerReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: BudgetPromptNotifier
    @Inject lateinit var check: MonthStartBudgetCheck

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.moneyrecords.app.debug.SHOW_BUDGET_PROMPT" -> notifier.show(YearMonth.now())
            "com.moneyrecords.app.debug.RUN_MONTH_START_CHECK" -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try { Log.i("MoneyRecords", "month-start check shown=${check.run()}") } finally { pending.finish() }
                }
            }
        }
    }
}
