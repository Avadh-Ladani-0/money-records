package com.moneyrecords.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneyrecords.app.MainActivity
import com.moneyrecords.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Month-start notification: "Keep last month's budgets?" with ✓ Keep / ✗ Edit.
 * ✓ just dismisses (budgets already carry forward); ✗ (or tapping it) opens the Budgets tab.
 */
@Singleton
class BudgetPromptNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(month: YearMonth) {
        if (!canNotify()) return
        ensureChannel()
        val previous = month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val current = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val openBudgets = PendingIntent.getActivity(
            context, REQUEST_EDIT,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_BUDGETS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val keep = PendingIntent.getBroadcast(
            context, REQUEST_KEEP,
            Intent(context, BudgetPromptReceiver::class.java).setAction(ACTION_KEEP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Keep last month's budgets?")
            .setContentText("Use $previous's budget limits for $current too.")
            .setContentIntent(openBudgets)
            .addAction(0, "✓ Keep", keep)
            .addAction(0, "✗ Edit", openBudgets)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    fun dismiss() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Budgets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Month-start budget check and budget alerts"
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "budgets"
        const val NOTIFICATION_ID = 4001
        const val ACTION_OPEN_BUDGETS = "com.moneyrecords.app.action.OPEN_BUDGETS"
        const val ACTION_KEEP = "com.moneyrecords.app.action.KEEP_BUDGETS"
        private const val REQUEST_EDIT = 1
        private const val REQUEST_KEEP = 2
    }
}

/** Handles "✓ Keep": budgets already carry forward, so it only dismisses the notification. */
class BudgetPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BudgetPromptNotifier.ACTION_KEEP) {
            NotificationManagerCompat.from(context).cancel(BudgetPromptNotifier.NOTIFICATION_ID)
        }
    }
}
