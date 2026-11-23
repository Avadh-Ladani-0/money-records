package com.moneyrecords.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.domain.detect.DetectStatus
import com.moneyrecords.app.domain.repository.SplitRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Later / Ignore / "Repaid by Rahul" on a caught payment, without opening the app. */
@AndroidEntryPoint
class DetectedActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RoomDetectionRepository
    @Inject lateinit var notifier: DetectedNotifier
    @Inject lateinit var splits: SplitRepository
    @Inject lateinit var prefs: UserPrefsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifications.EXTRA_DETECTED_ID, 0L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Notifications.ACTION_DETECTED_LATER -> repository.setStatus(id, DetectStatus.LATER)
                    Notifications.ACTION_DETECTED_IGNORE -> repository.setStatus(id, DetectStatus.IGNORED)
                    Notifications.ACTION_DETECTED_REPAID -> repaid(id)
                }
                notifier.cancel(id)
                notifier.refreshSummary()
            } finally {
                pending.finish()
            }
        }
    }

    /** Apply a credit to the bills its sender owes (oldest first), like "Received from Rahul". */
    private suspend fun repaid(id: Long) {
        val d = repository.get(id) ?: return
        val account = d.accountId ?: return
        val who = d.counterparty?.substringBefore('@')?.lowercase()?.trim() ?: return
        val open = splits.observeOpen().first().filter { p ->
            p.person != null && (p.person.lowercase() == who || who.startsWith(p.person.lowercase()) || p.person.lowercase().startsWith(who))
        }
        val person = open.groupBy { it.person!! }.maxByOrNull { (_, ps) -> ps.sumOf { it.outstanding } }?.key ?: return
        val owed = open.filter { it.person == person }.sumOf { it.outstanding }
        runCatching { splits.receiveFromPerson(person, minOf(d.amount, owed), account) }.onSuccess {
            prefs.setRepaymentAccount(account)
            repository.setStatus(id, DetectStatus.ADDED)
        }
    }
}
