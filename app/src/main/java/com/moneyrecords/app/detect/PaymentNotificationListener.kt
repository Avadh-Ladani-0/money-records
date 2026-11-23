package com.moneyrecords.app.detect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneyrecords.app.domain.detect.DetectSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads notifications from known payment and bank apps only ("Paid ₹150 to Raju", "Rahul paid you
 * ₹200"). Every other app's notifications are ignored without being looked at.
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {
    @Inject lateinit var pipeline: DetectionPipeline
    @Inject lateinit var settings: DetectionSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** The same notification is often re-posted (updates); handle each key + text once. */
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!PaymentApps.isPaymentApp(sbn.packageName)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT),
        ).joinToString(" · ").trim()
        if (text.isBlank()) return
        val key = sbn.key + "|" + text
        synchronized(seen) {
            if (!seen.add(key)) return
            if (seen.size > 200) seen.remove(seen.first())
        }
        scope.launch {
            if (settings.current().notifications) pipeline.onAlert(DetectSource.NOTIFICATION, sbn.packageName, text, sbn.postTime)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
