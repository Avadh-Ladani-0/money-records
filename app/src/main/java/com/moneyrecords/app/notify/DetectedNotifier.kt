package com.moneyrecords.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.detect.PaymentApps
import com.moneyrecords.app.domain.detect.Detected
import com.moneyrecords.app.domain.detect.Direction
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.SplitRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Paid ₹150 to Raju · SBI — Add / Later / Ignore". One notification per caught payment, plus an
 * ongoing "3 payments to record" while anything is waiting, so nothing is forgotten.
 */
@Singleton
class DetectedNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: dagger.Lazy<RoomDetectionRepository>,
    private val accounts: AccountRepository,
    private val splits: SplitRepository,
) : com.moneyrecords.app.domain.detect.DetectedPrompts {
    /** Someone named in [d] who owes you (split bills), and how much. */
    private suspend fun owing(d: Detected): Pair<String, Long>? {
        if (d.direction != Direction.IN || d.isTransfer) return null
        val who = d.counterparty?.substringBefore('@')?.lowercase()?.trim() ?: return null
        return splits.observeOpen().first()
            .filter { p -> p.person != null && (p.person.lowercase() == who || who.startsWith(p.person.lowercase()) || p.person.lowercase().startsWith(who)) }
            .groupBy { it.person!! }
            .map { (name, parts) -> name to parts.sumOf { it.outstanding } }
            .maxByOrNull { it.second }
    }

    suspend fun title(d: Detected): String =
        com.moneyrecords.app.domain.detect.DetectionText.title(d, accounts.observeAccounts().first().associate { it.id to it.name })

    suspend fun show(d: Detected) {
        val already = repository.get().looksRecorded(d)
        val owes = owing(d)
        val code = d.id.toInt() * 10
        val via = PaymentApps.nameOf(d.origin)?.let { "From $it" } ?: if (d.source.name == "SMS") "From a bank SMS" else ""
        val text = when {
            already -> "Looks like it's already recorded — check before adding."
            owes != null -> "${owes.first} owes you ${Money.format(owes.second)} — is this their repayment?"
            else -> "Add it to MoneyRecords?" + if (via.isNotBlank()) " · $via" else ""
        }
        val b = Notifications.builder(context, Notifications.CHANNEL_DETECTED)
            .setContentTitle(title(d))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.activityIntent(context, Notifications.ACTION_ADD_DETECTED, code + 1, extraDetected = d.id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (owes != null) {
            b.addAction(0, "Repaid by ${owes.first}", broadcast(Notifications.ACTION_DETECTED_REPAID, code + 2, d.id))
        } else {
            b.addAction(0, "Add", Notifications.activityIntent(context, Notifications.ACTION_ADD_DETECTED, code + 2, extraDetected = d.id))
        }
        b.addAction(0, "Later", broadcast(Notifications.ACTION_DETECTED_LATER, code + 3, d.id))
        b.addAction(0, "Ignore", broadcast(Notifications.ACTION_DETECTED_IGNORE, code + 4, d.id))
        Notifications.post(context, Notifications.detectedId(d.id), b.build())
        refreshSummary()
    }

    override fun cancel(id: Long) = Notifications.cancel(context, Notifications.detectedId(id))

    /** The ongoing "N payments to record" reminder; gone when nothing is waiting. */
    override suspend fun refreshSummary(alert: Boolean) {
        val open = repository.get().observeOpen().first()
        if (open.isEmpty()) {
            Notifications.cancel(context, Notifications.TO_RECORD_SUMMARY_ID)
            return
        }
        val n = Notifications.builder(context, Notifications.CHANNEL_DETECTED)
            .setContentTitle(if (open.size == 1) "1 payment to record" else "${open.size} payments to record")
            .setContentText("Tap to add or ignore them")
            .setContentIntent(Notifications.activityIntent(context, Notifications.ACTION_OPEN_TO_RECORD, 11))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        Notifications.post(context, Notifications.TO_RECORD_SUMMARY_ID, n)
    }

    private fun broadcast(action: String, requestCode: Int, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, DetectedActionReceiver::class.java).setAction(action).putExtra(Notifications.EXTRA_DETECTED_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
