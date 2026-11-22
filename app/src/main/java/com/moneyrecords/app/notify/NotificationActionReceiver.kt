package com.moneyrecords.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.moneyrecords.app.domain.repository.RecurringRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/** Handles "✓ Add" and "Skip" on a recurring-due notification without opening the app. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var recurring: RecurringRepository
    @Inject lateinit var notifier: RecurringNotifier
    @Inject lateinit var alerts: BudgetAlerts
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(Notifications.EXTRA_RULE_ID, 0L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Notifications.ACTION_RECURRING_ADD -> {
                        val rule = recurring.get(ruleId)
                        recurring.confirm(ruleId)
                        rule?.let { alerts.checkAfterRecord(YearMonth.from(it.nextRunDate)) }
                    }
                    Notifications.ACTION_RECURRING_SKIP -> recurring.skip(ruleId)
                }
                notifier.cancelDue(ruleId)
            } catch (e: IllegalArgumentException) {
                // Already handled elsewhere (e.g. added from the app) — just clear the notification.
                Log.i("MoneyRecords", "recurring action ignored: ${e.message}")
                notifier.cancelDue(ruleId)
            } finally {
                pending.finish()
            }
        }
    }
}
