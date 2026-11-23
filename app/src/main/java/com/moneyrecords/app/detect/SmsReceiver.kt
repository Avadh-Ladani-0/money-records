package com.moneyrecords.app.detect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.moneyrecords.app.domain.detect.DetectSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * New SMS from bank senders. Only alphanumeric sender IDs ("AD-SBIUPI") are looked at — messages
 * from people's phone numbers are never read. Non-transaction texts are dropped by the parser.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var pipeline: DetectionPipeline
    @Inject lateinit var settings: DetectionSettings

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = parts.groupBy { it.originatingAddress.orEmpty() }
            .filterKeys(SmsSources::isBankSender)
            .mapValues { (_, ms) -> ms.joinToString("") { it.messageBody.orEmpty() } to (ms.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()) }
        if (bySender.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settings.current().sms) {
                    bySender.forEach { (sender, body) -> pipeline.onAlert(DetectSource.SMS, sender, body.first, body.second) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object SmsSources {
    /** Bank / business senders use letters ("VM-SBIUPI", "BOBTXN"); people use phone numbers. */
    fun isBankSender(address: String): Boolean = address.any { it.isLetter() }

    private val banks = listOf("SBI", "BOB", "KCB", "KCC", "HDFC", "ICICI", "AXIS", "KOTAK", "PNB", "IDFC", "YES", "CANARA", "UNION")

    /**
     * One key per bank for all its senders: "JD-SBIUPI-S", "JK-ATMSBI-S" → "sms:SBI"; "JX-KCBANK-S" →
     * "sms:KCB". Unknown senders keep their own name ("sms:RZRPAY").
     */
    fun bankKey(address: String): String {
        val core = address.uppercase().removePrefix("+").split('-').let { parts ->
            if (parts.size >= 2 && parts[0].length == 2) parts[1] else parts.firstOrNull().orEmpty()
        }
        val bank = banks.firstOrNull { it in core }?.let { if (it == "KCC") "KCB" else it }
        return "sms:" + (bank ?: core)
    }
}
