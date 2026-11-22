package com.moneyrecords.app.work

import android.content.Context
import com.moneyrecords.app.domain.repository.NotificationPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Re-plans the daily reminder whenever its settings change. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    fun apply(prefs: NotificationPrefs) {
        if (prefs.dailyReminder) {
            DailyReminderWorker.schedule(context, prefs.reminderHour, prefs.reminderMinute, clock, replace = true)
        } else {
            DailyReminderWorker.cancel(context)
        }
    }
}
