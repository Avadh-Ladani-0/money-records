package com.moneyrecords.app.detect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.domain.detect.DetectStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After a Scan & pay tap, notices when you leave the payment app by swiping up (not via Back, which
 * returns to MoneyRecords and opens the record dialog) and asks right away. Uses Android's usage
 * access only to see which app is in front, only for a few minutes after a tap.
 */
@Singleton
class PayWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: DetectionPipeline,
    private val repository: RoomDetectionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Watch [pkg] after a tap that created item [detectedId]. */
    fun watch(pkg: String, detectedId: Long) {
        if (!hasAccess()) return
        job?.cancel()
        job = scope.launch {
            val usage = context.getSystemService(UsageStatsManager::class.java)
            val start = System.currentTimeMillis()
            var since = start - 2_000
            var seenTarget = false
            while (System.currentTimeMillis() - start < WATCH_MS) {
                delay(POLL_MS)
                val now = System.currentTimeMillis()
                val events = usage.queryEvents(since, now)
                since = now
                val e = UsageEvents.Event()
                var front: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) front = e.packageName
                }
                when {
                    front == null -> Unit
                    front == pkg -> seenTarget = true
                    // Back to MoneyRecords: the record dialog takes it from here.
                    front == context.packageName -> if (seenTarget) return@launch
                    // Swiped away to another app or the home screen: ask now (once).
                    seenTarget -> {
                        val item = repository.get(detectedId)
                        if (item != null && item.status == DetectStatus.NEW) pipeline.resolve(detectedId)
                        return@launch
                    }
                }
            }
        }
    }

    private companion object {
        const val WATCH_MS = 10 * 60_000L
        const val POLL_MS = 1_200L
    }
}
