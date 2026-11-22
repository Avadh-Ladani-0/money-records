package com.moneyrecords.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.notify.RecurringNotifier
import com.moneyrecords.app.notify.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Recurring checks: posts a "due" notification for each rule with an occurrence due (from 9 AM on its
 * date; missed days caught up) and an "upcoming" heads-up the day before a recurring expense.
 * Each occurrence is notified once (remembered via [UserPrefsRepository.markSent]).
 */
class RecurringCheck @Inject constructor(
    private val recurring: RecurringRepository,
    private val prefs: UserPrefsRepository,
    private val notifier: RecurringNotifier,
    private val clock: Clock,
) {
    suspend fun run() {
        val settings = prefs.notifications()
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        if (now.hour < NOTIFY_FROM_HOUR) return
        if (settings.recurringDue) {
            // One notification per rule, for its earliest due occurrence.
            recurring.observeDue(today).first().groupBy { it.details.rule.id }.values.forEach { occurrences ->
                val first = occurrences.first()
                if (prefs.markSent("due:${first.details.rule.id}:${first.date}")) notifier.showDue(first, today)
            }
        }
        if (settings.upcomingBills) {
            val tomorrow = today.plusDays(1)
            recurring.observeDue(tomorrow).first()
                .filter { it.date == tomorrow && it.details.rule.kind == RecordKind.EXPENSE }
                .forEach { if (prefs.markSent("upcoming:${it.details.rule.id}:${it.date}")) notifier.showUpcoming(it) }
        }
    }

    companion object {
        const val NOTIFY_FROM_HOUR = 9
    }
}

@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val check: RecurringCheck,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        check.run()
        return Result.success()
    }

    companion object {
        private const val NAME = "recurring-check"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** The daily "log today's spending" nudge, skipped on days that already have records. */
class DailyReminderCheck @Inject constructor(
    private val records: RecordRepository,
    private val prefs: UserPrefsRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    /** Returns true if the reminder was shown. */
    suspend fun run(): Boolean {
        if (!prefs.notifications().dailyReminder) return false
        val today = LocalDateTime.now(clock).toLocalDate()
        if (records.countOn(today) > 0) return false
        if (!prefs.markSent("daily:$today")) return false
        notifier.showDaily()
        return true
    }
}

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val check: DailyReminderCheck,
    private val prefs: UserPrefsRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        check.run()
        // Chain the next one for tomorrow at the chosen time.
        val n = prefs.notifications()
        if (n.dailyReminder) schedule(applicationContext, n.reminderHour, n.reminderMinute, clock, replace = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reminder"

        /** Delay from [now] until the next [hour]:[minute] (today if still ahead, else tomorrow). */
        fun delayUntil(now: LocalDateTime, hour: Int, minute: Int): Duration {
            var target = now.toLocalDate().atTime(hour, minute)
            if (!target.isAfter(now.plusMinutes(1))) target = target.plusDays(1)
            return Duration.between(now, target)
        }

        fun schedule(context: Context, hour: Int, minute: Int, clock: Clock = Clock.systemDefaultZone(), replace: Boolean = false) {
            val delay = delayUntil(LocalDateTime.now(clock), hour, minute)
            val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
