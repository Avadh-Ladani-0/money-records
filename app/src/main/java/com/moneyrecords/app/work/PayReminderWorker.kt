package com.moneyrecords.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneyrecords.app.data.repository.RoomDetectionRepository
import com.moneyrecords.app.domain.detect.DetectStatus
import com.moneyrecords.app.notify.DetectedNotifier
import com.moneyrecords.app.notify.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** A few minutes after a pay-widget tap: if it still isn't recorded, ask about it. */
@HiltWorker
class PayReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RoomDetectionRepository,
    private val pipeline: com.moneyrecords.app.detect.DetectionPipeline,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(Notifications.EXTRA_DETECTED_ID, 0L)
        val item = repository.get(id) ?: return Result.success()
        // Still not recorded: settle its account by the app's rules and ask.
        if (item.status == DetectStatus.NEW) pipeline.resolve(id)
        return Result.success()
    }
}
