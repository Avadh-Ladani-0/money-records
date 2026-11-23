package com.moneyrecords.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneyrecords.app.MainActivity
import com.moneyrecords.app.R
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.recurring.Recurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Channels, permission check and intents shared by all MoneyRecords notifications. */
object Notifications {
    const val CHANNEL_BUDGETS = "budgets"
    const val CHANNEL_RECURRING = "recurring"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_BACKUP = "backup"
    const val CHANNEL_DETECTED = "detected"
    const val ACTION_ADD_DETECTED = "com.moneyrecords.app.action.ADD_DETECTED"
    const val ACTION_OPEN_TO_RECORD = "com.moneyrecords.app.action.OPEN_TO_RECORD"
    const val ACTION_OPEN_AUTO_DETECT = "com.moneyrecords.app.action.OPEN_AUTO_DETECT"
    const val ACTION_DETECTED_LATER = "com.moneyrecords.app.action.DETECTED_LATER"
    const val ACTION_DETECTED_IGNORE = "com.moneyrecords.app.action.DETECTED_IGNORE"
    const val ACTION_DETECTED_REPAID = "com.moneyrecords.app.action.DETECTED_REPAID"
    const val EXTRA_DETECTED_ID = "detectedId"
    fun detectedId(id: Long) = 500_000 + id.toInt()
    const val TO_RECORD_SUMMARY_ID = 5001
    const val ACTION_OPEN_BACKUP = "com.moneyrecords.app.action.OPEN_BACKUP"

    // Activity actions (handled by MainActivity).
    const val ACTION_OPEN_BUDGETS = "com.moneyrecords.app.action.OPEN_BUDGETS"
    const val ACTION_EDIT_DUE = "com.moneyrecords.app.action.EDIT_DUE"
    const val ACTION_ADD_RECORD = "com.moneyrecords.app.action.ADD_RECORD"
    const val ACTION_OPEN_HOME = "com.moneyrecords.app.action.OPEN_HOME"
    const val EXTRA_KIND = "kind"
    // Broadcast actions (handled by NotificationActionReceiver).
    const val ACTION_RECURRING_ADD = "com.moneyrecords.app.action.RECURRING_ADD"
    const val ACTION_RECURRING_SKIP = "com.moneyrecords.app.action.RECURRING_SKIP"
    const val EXTRA_RULE_ID = "ruleId"

    fun dueId(ruleId: Long) = 100_000 + ruleId.toInt()
    fun upcomingId(ruleId: Long) = 200_000 + ruleId.toInt()
    const val DAILY_ID = 3001
    const val BACKUP_ID = 4001

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_BUDGETS, "Budgets", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Month-start budget check and 80% / over-budget alerts" },
            NotificationChannel(CHANNEL_RECURRING, "Recurring records", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Recurring records that are due, and bills due tomorrow" },
            NotificationChannel(CHANNEL_REMINDERS, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A daily nudge to log your spending" },
            NotificationChannel(CHANNEL_BACKUP, "Backups", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Problems with automatic backups" },
            NotificationChannel(CHANNEL_DETECTED, "Payments to record", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Payments and credits caught from bank SMS, payment apps and pay widgets" },
        ).forEach { if (manager.getNotificationChannel(it.id) == null) manager.createNotificationChannel(it) }
    }

    fun activityIntent(context: Context, action: String, requestCode: Int, ruleId: Long? = null, extraDetected: Long? = null): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .apply { if (ruleId != null) putExtra(EXTRA_RULE_ID, ruleId) }
                .apply { if (extraDetected != null) putExtra(EXTRA_DETECTED_ID, extraDetected) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun broadcastIntent(context: Context, action: String, requestCode: Int, ruleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_RULE_ID, ruleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canNotify(context)) return
        ensureChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun builder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .hideOnLockScreen(context, channel)

    /** On a locked phone show only "MoneyRecords · New notification"; full text after unlocking. */
    fun NotificationCompat.Builder.hideOnLockScreen(context: Context, channel: String): NotificationCompat.Builder =
        setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MoneyRecords")
                .setContentText("New notification")
                .build(),
        )
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "Rent · ₹15,000 due today" with ✓ Add / Skip / Edit, and "due tomorrow" heads-ups. */
@Singleton
class RecurringNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun showDue(occurrence: DueOccurrence, today: LocalDate) {
        val d = occurrence.details
        val rule = d.rule
        val whenText = when (occurrence.date) {
            today -> "due today"
            else -> "due ${occurrence.date.format(shortDate)}"
        }
        val ruleId = rule.id
        val code = ruleId.toInt() * 10
        val n = Notifications.builder(context, Notifications.CHANNEL_RECURRING)
            .setContentTitle("${d.title} · ${Money.format(rule.amount)} $whenText")
            .setContentText("${d.accountName} · ${Recurrence.describe(rule)}")
            .setContentIntent(Notifications.activityIntent(context, Notifications.ACTION_EDIT_DUE, code + 1, ruleId))
            .addAction(0, "✓ Add", Notifications.broadcastIntent(context, Notifications.ACTION_RECURRING_ADD, code + 2, ruleId))
            .addAction(0, "Skip", Notifications.broadcastIntent(context, Notifications.ACTION_RECURRING_SKIP, code + 3, ruleId))
            .addAction(0, "Edit", Notifications.activityIntent(context, Notifications.ACTION_EDIT_DUE, code + 4, ruleId))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        Notifications.post(context, Notifications.dueId(ruleId), n)
    }

    fun showUpcoming(occurrence: DueOccurrence) {
        val d = occurrence.details
        val verb = if (d.rule.kind == RecordKind.EXPENSE) "due" else "expected"
        val n = Notifications.builder(context, Notifications.CHANNEL_RECURRING)
            .setContentTitle("${d.title} · ${Money.format(d.rule.amount)} $verb tomorrow")
            .setContentText("From ${d.accountName}")
            .setContentIntent(Notifications.activityIntent(context, Notifications.ACTION_ADD_RECORD, d.rule.id.toInt() * 10 + 5))
            .build()
        Notifications.post(context, Notifications.upcomingId(d.rule.id), n)
    }

    fun cancelDue(ruleId: Long) = Notifications.cancel(context, Notifications.dueId(ruleId))
}

/** "Log today's spending" — skipped on days with records. */
@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun showDaily() {
        val add = Notifications.activityIntent(context, Notifications.ACTION_ADD_RECORD, 7)
        val n = Notifications.builder(context, Notifications.CHANNEL_REMINDERS)
            .setContentTitle("Log today's spending")
            .setContentText("Nothing recorded yet today — it takes a few seconds.")
            .setContentIntent(add)
            .addAction(0, "Add record", add)
            .build()
        Notifications.post(context, Notifications.DAILY_ID, n)
    }
}

/** Posts budget threshold alerts; an interface so the checker can be unit-tested. */
interface BudgetAlertPoster {
    fun post(progress: BudgetProgress, threshold: Int)
}

@Singleton
class BudgetAlertNotifier @Inject constructor(@ApplicationContext private val context: Context) : BudgetAlertPoster {
    override fun post(progress: BudgetProgress, threshold: Int) {
        val name = when (progress.budget.scope) {
            BudgetScope.OVERALL -> "Your overall budget"
            BudgetScope.REST -> "Everything else"
            BudgetScope.OTHER_ACCOUNTS -> "Other accounts"
            BudgetScope.CATEGORY -> progress.categoryName ?: "A category budget"
            BudgetScope.ACCOUNT -> progress.accountName ?: "An account budget"
        }
        val title = if (threshold >= 100) "$name is over budget" else "$name: ${(progress.fraction * 100).toInt()}% used"
        val detail = "${Money.format(progress.spent)} of ${Money.format(progress.budget.limit)} · " +
            if (progress.isOver) "${Money.format(-progress.remaining)} over" else "${Money.format(progress.remaining)} left"
        val n = Notifications.builder(context, Notifications.CHANNEL_BUDGETS)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(Notifications.activityIntent(context, Notifications.ACTION_OPEN_BUDGETS, 8))
            .build()
        Notifications.post(context, ("${progress.budget.target}").hashCode(), n)
    }
}

/** "Automatic backup failed" — the folder was moved, deleted or its access removed. */
@Singleton
class BackupNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun showFolderLost() {
        val open = Notifications.activityIntent(context, Notifications.ACTION_OPEN_BACKUP, 9)
        val n = Notifications.builder(context, Notifications.CHANNEL_BACKUP)
            .setContentTitle("Automatic backup didn't run")
            .setContentText("MoneyRecords can't reach the backup folder. Tap to pick it again.")
            .setContentIntent(open)
            .build()
        Notifications.post(context, Notifications.BACKUP_ID, n)
    }
}
