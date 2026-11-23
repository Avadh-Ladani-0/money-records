package com.moneyrecords.app.backup

import android.content.Context
import androidx.room.withTransaction
import com.moneyrecords.app.BuildConfig
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.security.AppLockManager
import com.moneyrecords.app.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** A readable backup and what's in it. */
data class BackupPreview(val file: BackupFile, val counts: BackupCounts)

/**
 * Makes and restores backups. Restoring replaces everything, so it first saves the current data to
 * app-private storage (the last 3 are kept), and the whole replace runs in one transaction — if any
 * step fails, nothing changes.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MoneyRecordsDatabase,
    private val prefs: UserPrefsRepository,
    private val appLock: AppLockManager,
    private val secrets: SecretStore,
    private val clock: Clock,
) {
    private val dao get() = db.backupDao()

    /** The saved backup password, if one is set. */
    suspend fun savedPassword(): String? = prefs.observeBackup().first().wrappedPassword?.let(secrets::unwrap)

    suspend fun snapshot(): BackupFile {
        val data = db.withTransaction {
            BackupData(
                accounts = dao.accounts().map { it.toRow() },
                categories = dao.categories().map { it.toRow() },
                recurringRules = dao.recurringRules().map { it.toRow() },
                records = dao.records().map { it.toRow() },
                budgets = dao.budgets().map { it.toRow() },
                budgetOffsets = dao.budgetOffsets().map { it.toRow() },
            )
        }
        val n = prefs.notifications()
        val lock = appLock.settings.value
        val calculator = prefs.observeCalculator().first()
        return BackupFile(
            appVersion = BuildConfig.VERSION_NAME,
            dbVersion = DB_VERSION,
            createdAt = clock.millis(),
            data = data,
            settings = BackupSettings(
                theme = prefs.observeTheme().first(),
                calculatorEnabled = calculator.enabled,
                relockAfterSeconds = lock.relockAfterSeconds,
                hideInRecents = lock.hideInRecents,
                notifyRecurringDue = n.recurringDue,
                notifyUpcomingBills = n.upcomingBills,
                notifyDailyReminder = n.dailyReminder,
                reminderHour = n.reminderHour,
                reminderMinute = n.reminderMinute,
                notifyBudgetAlerts = n.budgetAlerts,
                notifyMonthStart = n.monthStartBudgets,
            ),
        )
    }

    /** The bytes of a new backup, encrypted when a backup password is set. */
    suspend fun createBackup(): ByteArray = BackupCodec.encode(snapshot(), savedPassword())

    /** Reads a file without changing anything. Throws [BackupError]. */
    fun preview(bytes: ByteArray, password: String?): BackupPreview {
        val file = BackupCodec.decode(bytes, password)
        return BackupPreview(file, file.data.counts())
    }

    /** Replaces all data with [file]'s. Returns what was restored. */
    suspend fun restore(file: BackupFile): BackupCounts {
        saveSafetyCopy()
        val d = file.data
        db.withTransaction {
            dao.clearBudgetOffsets()
            dao.clearBudgets()
            dao.clearRecords()
            dao.clearRecurringRules()
            dao.clearCategories()
            dao.clearAccounts()
            dao.insertAccounts(d.accounts.map { it.toEntity() })
            dao.insertCategories(d.categories.parentsFirst().map { it.toEntity() })
            dao.insertRecurringRules(d.recurringRules.map { it.toEntity() })
            dao.insertRecords(d.records.map { it.toEntity() })
            dao.insertBudgets(d.budgets.map { it.toEntity() })
            dao.insertBudgetOffsets(d.budgetOffsets.map { it.toEntity() })
        }
        restoreSettings(file.settings)
        return d.counts()
    }

    private suspend fun restoreSettings(s: BackupSettings) {
        s.theme?.let { prefs.setTheme(it) }
        s.calculatorEnabled?.let { prefs.setCalculatorEnabled(it) }
        // The lock itself stays as it is on this phone; only its preferences come back.
        appLock.update { lock ->
            lock.copy(
                relockAfterSeconds = s.relockAfterSeconds ?: lock.relockAfterSeconds,
                hideInRecents = s.hideInRecents ?: lock.hideInRecents,
            )
        }
        prefs.updateNotifications { n ->
            n.copy(
                recurringDue = s.notifyRecurringDue ?: n.recurringDue,
                upcomingBills = s.notifyUpcomingBills ?: n.upcomingBills,
                dailyReminder = s.notifyDailyReminder ?: n.dailyReminder,
                reminderHour = s.reminderHour ?: n.reminderHour,
                reminderMinute = s.reminderMinute ?: n.reminderMinute,
                budgetAlerts = s.notifyBudgetAlerts ?: n.budgetAlerts,
                monthStartBudgets = s.notifyMonthStart ?: n.monthStartBudgets,
            )
        }
    }

    /** The current data, unencrypted, in app-private storage — the undo for a restore. */
    private suspend fun saveSafetyCopy() {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        File(dir, "before-restore-${clock.millis()}.json").writeBytes(BackupCodec.encode(snapshot()))
        dir.listFiles { f -> f.name.startsWith("before-restore-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_SAFETY_COPIES)
            ?.forEach { it.delete() }
    }

    companion object {
        const val DB_VERSION = 6
        const val KEEP_SAFETY_COPIES = 3
    }
}
