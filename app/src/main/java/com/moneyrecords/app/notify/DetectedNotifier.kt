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
import kotlinx.coroutines.launch
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

    /** Items whose record dialog is open right now — their prompt stays hidden. */
    private val inDialog = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    override fun editing(id: Long, open: Boolean) {
        if (open) { inDialog += id; cancel(id) } else inDialog -= id
    }

    private fun isOpen(status: com.moneyrecords.app.domain.detect.DetectStatus) =
        status == com.moneyrecords.app.domain.detect.DetectStatus.NEW || status == com.moneyrecords.app.domain.detect.DetectStatus.LATER

    suspend fun show(d: Detected) {
        if (d.id in inDialog) return
        val already = repository.get().looksRecorded(d)
        val owes = owing(d)
        val code = d.id.toInt() * 10
        val via = PaymentApps.nameOf(d.origin)?.let { "From $it" } ?: if (d.source.name == "SMS") "From a bank SMS" else ""
        val text = when {
            d.source == com.moneyrecords.app.domain.detect.DetectSource.PAY_WIDGET && d.amount == 0L ->
                "Paid with ${PaymentApps.nameOf(d.origin) ?: "a payment app"}? Tap Add to record it."
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
            // Shown again (reminders) without buzzing again.
            .setOnlyAlertOnce(true)
        // A Scan & pay payment stays until it's recorded or marked "No payment made".
        val widgetPayment = d.source == com.moneyrecords.app.domain.detect.DetectSource.PAY_WIDGET
        if (widgetPayment) b.setOngoing(true).setAutoCancel(false)
        if (owes != null) {
            b.addAction(0, "Repaid by ${owes.first}", broadcast(Notifications.ACTION_DETECTED_REPAID, code + 2, d.id))
        } else {
            b.addAction(0, "Add", Notifications.activityIntent(context, Notifications.ACTION_ADD_DETECTED, code + 2, extraDetected = d.id))
        }
        if (widgetPayment) {
            b.addAction(0, "No payment made", broadcast(Notifications.ACTION_DETECTED_IGNORE, code + 4, d.id))
        } else {
            b.addAction(0, "Later", broadcast(Notifications.ACTION_DETECTED_LATER, code + 3, d.id))
            b.addAction(0, "Ignore", broadcast(Notifications.ACTION_DETECTED_IGNORE, code + 4, d.id))
        }
        // Handled meanwhile (recorded / dismissed while this was being prepared)? Then don't post it.
        val now = repository.get().get(d.id)
        if (now == null || !isOpen(now.status) || d.id in inDialog) { cancel(d.id); refreshSummary(); return }
        Notifications.post(context, Notifications.detectedId(d.id), b.build())
        refreshSummary()
    }

    override fun cancel(id: Long) = Notifications.cancel(context, Notifications.detectedId(id))

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun remindIfOpen(id: Long) {
        scope.launch {
            val d = repository.get().get(id) ?: return@launch
            if (d.status == com.moneyrecords.app.domain.detect.DetectStatus.NEW || d.status == com.moneyrecords.app.domain.detect.DetectStatus.LATER) {
                show(d.copy(accountId = d.accountId ?: repository.get().guessAccount(d)))
            }
        }
    }

    /** The ongoing "N payments to record" reminder; gone when nothing is waiting. */
    override suspend fun refreshSummary(alert: Boolean) {
        val open = repository.get().observeOpen().first()
        // Sweep: clear any prompt whose payment is no longer waiting.
        val openIds = open.map { Notifications.detectedId(it.id) }.toSet()
        runCatching {
            val active = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
            active.filter { it.id in 500_000 until 1_000_000 && it.id !in openIds }
                .forEach { Notifications.cancel(context, it.id) }
        }.onFailure { android.util.Log.w("MoneyRecords", "sweep failed", it) }
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
