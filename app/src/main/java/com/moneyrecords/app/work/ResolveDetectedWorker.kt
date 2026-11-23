package com.moneyrecords.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneyrecords.app.detect.DetectionPipeline
import com.moneyrecords.app.notify.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** After an app's payment alert waited for its bank SMS: settle the account and ask the user. */
@HiltWorker
class ResolveDetectedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: DetectionPipeline,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        pipeline.resolve(inputData.getLong(Notifications.EXTRA_DETECTED_ID, 0L))
        return Result.success()
    }
}
