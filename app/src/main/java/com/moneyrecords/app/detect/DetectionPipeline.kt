package com.moneyrecords.app.detect

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.domain.detect.DetectSource
import com.moneyrecords.app.domain.detect.DetectStatus
import com.moneyrecords.app.domain.detect.Detected
import com.moneyrecords.app.domain.detect.DetectionRules
import com.moneyrecords.app.domain.detect.Direction
import com.moneyrecords.app.domain.detect.TransactionParser
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.UpiLite
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.notify.DetectedNotifier
import com.moneyrecords.app.notify.Notifications
import com.moneyrecords.app.work.ResolveDetectedWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a bank SMS or payment-app notification into a "to record" item and asks the user.
 * Text that isn't clearly a completed payment or credit is dropped and never stored.
 *
 * Which account: a bank SMS names it (digits, or the bank learnt from earlier SMS). An app alert
 * waits [DetectionRules.SMS_WAIT_MS] for that SMS; if none comes, the app's rules decide (UPI Lite for
 * small payments from the app that has it — Lite payments never send an SMS — else the usual bank).
 */
@Singleton
class DetectionPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RoomDetectionRepository,
    private val accounts: AccountRepository,
    private val settings: DetectionSettings,
    private val notifier: DetectedNotifier,
    private val clock: Clock,
) {
    private suspend fun lite(): Account? = accounts.observeAccounts().first().firstOrNull { it.isUpiLite }

    suspend fun onAlert(source: DetectSource, origin: String, text: String, at: Long = clock.millis(), prompt: Boolean = true): Detected? {
        val parsed = TransactionParser.parse(text) ?: return null
        val prefs = settings.current()
        val accountList = accounts.observeAccounts().first()
        val lite = accountList.firstOrNull { it.isUpiLite }
        val digits = accountList.mapNotNull { a -> a.digits?.let { a.id to it } }.toMap()
        val key = if (source == DetectSource.SMS) SmsSources.bankKey(origin) else origin
        var accountId = DetectionRules.resolveAccount(parsed.accountDigits, key, digits, prefs.smsBanks)
        // Learn which bank an SMS sender belongs to, so its messages without digits (top-ups) still match.
        if (source == DetectSource.SMS && parsed.accountDigits != null && accountId != null && prefs.smsBanks[key] == null) {
            settings.learnSmsBank(key, accountId)
        }
        var toAccount: Long? = null
        var counterparty = parsed.counterparty
        when {
            // "UPI LITE Top-up on SBI": money moves from the funding bank into UPI Lite.
            lite != null && parsed.upiLite && parsed.direction == Direction.OUT && !parsed.reversal -> {
                accountId = accountId ?: lite.fundingAccountId
                toAccount = lite.id.takeIf { it != accountId }
                counterparty = null
            }
            // Lite balance returned to the bank (Lite turned off): a transfer back.
            lite != null && parsed.upiLite && parsed.direction == Direction.IN && !parsed.reversal -> {
                toAccount = accountId ?: lite.fundingAccountId
                accountId = lite.id
                counterparty = null
            }
        }
        // Money never arrives in UPI Lite from others (credits go to the bank).
        if (parsed.direction == Direction.IN && toAccount == null && accountId == lite?.id) accountId = null
        val item = Detected(
            source = source, origin = origin,
            direction = if (toAccount != null) Direction.OUT else parsed.direction,
            amount = parsed.amount, accountId = accountId, toAccountId = toAccount,
            accountDigits = parsed.accountDigits, counterparty = counterparty, ref = parsed.ref,
            upiLite = parsed.upiLite, occurredAt = at, text = text.trim(),
        )
        val result = repository.ingest(item)
        val merged = result.item
        when {
            !result.notify -> Unit
            // An app alert without a known account: wait for the bank SMS, then decide.
            source == DetectSource.NOTIFICATION && merged.accountId == null -> scheduleResolve(merged.id)
            prompt -> { cancelResolve(merged.id); notifier.show(merged) }
            else -> notifier.refreshSummary(alert = false)
        }
        // A small top-up is "just enough to pay": a UPI Lite payment happened right then.
        if (lite != null && toAccount == lite.id && parsed.amount < UpiLite.SHORTFALL_TOP_UP && prompt) {
            val hint = repository.ingest(
                Detected(
                    source = DetectSource.PAY_WIDGET, origin = lite.liteApp ?: origin, direction = Direction.OUT,
                    amount = 0, accountId = lite.id, occurredAt = at, text = "Paid with UPI Lite just now?",
                ),
            ).item
            scheduleResolve(hint.id)
        }
        return merged
    }

    /** A pay-widget tap: a payment via [pkg]; amount and account come from its alerts (or the rules). */
    suspend fun startPayment(pkg: String): Detected {
        val item = Detected(
            source = DetectSource.PAY_WIDGET, origin = pkg, direction = Direction.OUT, amount = 0,
            occurredAt = clock.millis(), text = "Paid with ${PaymentApps.nameOf(pkg) ?: "a payment app"}",
        )
        return repository.ingest(item).item
    }

    /** No bank SMS came: decide the account by the app's rules (UPI Lite / usual bank) and ask. */
    suspend fun resolve(id: Long) {
        val item = repository.get(id) ?: return
        if (item.status != DetectStatus.NEW) return
        val resolved = item.copy(accountId = repository.guessAccount(item))
        if (resolved != item) repository.update(resolved)
        notifier.show(resolved)
    }

    private fun workName(id: Long) = "resolve-detected-$id"

    private fun scheduleResolve(id: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ResolveDetectedWorker>()
                .setInitialDelay(DetectionRules.SMS_WAIT_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(Notifications.EXTRA_DETECTED_ID to id))
                .build(),
        )
    }

    private fun cancelResolve(id: Long) = WorkManager.getInstance(context).cancelUniqueWork(workName(id))
}

/** Reads recent bank SMS once (when SMS detection is turned on), so the last days aren't missed. */
@Singleton
class SmsBackfill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: DetectionPipeline,
    private val clock: Clock,
) {
    suspend fun run(days: Int = 2): Int {
        val since = clock.millis() - days * 24L * 60 * 60_000
        var found = 0
        runCatching {
            context.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(android.provider.Telephony.Sms.ADDRESS, android.provider.Telephony.Sms.BODY, android.provider.Telephony.Sms.DATE),
                "${android.provider.Telephony.Sms.DATE} > ?", arrayOf(since.toString()),
                "${android.provider.Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val address = c.getString(0).orEmpty()
                    if (!SmsSources.isBankSender(address)) continue
                    if (pipeline.onAlert(DetectSource.SMS, address, c.getString(1).orEmpty(), c.getLong(2), prompt = false) != null) found++
                }
            }
        }
        return found
    }
}
