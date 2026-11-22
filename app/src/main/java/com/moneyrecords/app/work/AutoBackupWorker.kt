package com.moneyrecords.app.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyrecords.app.backup.BackupFolder
import com.moneyrecords.app.backup.BackupManager
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.notify.BackupNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Weekly backup into the folder the user picked. Records the outcome for Settings → Backup, and
 * asks (by notification) to pick the folder again if access to it was lost.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
    private val prefs: UserPrefsRepository,
    private val notifier: BackupNotifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.observeBackup().first()
        val folder = settings.folderUri ?: return Result.success()
        if (!settings.autoEnabled && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
        val outcome = runCatching {
            BackupFolder.write(applicationContext, Uri.parse(folder), LocalDate.now(clock), backups.createBackup())
        }
        prefs.updateBackup {
            it.copy(
                lastBackupAtMillis = if (outcome.isSuccess) clock.millis() else it.lastBackupAtMillis,
                lastResult = if (outcome.isSuccess) "ok" else "Folder no longer available — pick it again",
            )
        }
        if (outcome.isFailure) notifier.showFolderLost()
        return Result.success()
    }

    companion object {
        private const val NAME = "auto-backup"
        private const val NAME_NOW = "auto-backup-now"
        private const val KEY_FORCE = "force"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)

        /** A backup to the folder right now (the "Back up to folder now" button). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(androidx.work.workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME_NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
