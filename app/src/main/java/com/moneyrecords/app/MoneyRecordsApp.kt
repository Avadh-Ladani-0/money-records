package com.moneyrecords.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.security.AppLockManager
import com.moneyrecords.app.widget.WidgetUpdater
import com.moneyrecords.app.work.AutoBackupWorker
import com.moneyrecords.app.work.DailyReminderWorker
import kotlinx.coroutines.flow.first
import com.moneyrecords.app.work.MonthStartBudgetWorker
import com.moneyrecords.app.work.RecurringWorker
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyRecordsApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // WorkManager is initialised on demand with Hilt's factory (default initializer removed in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject lateinit var prefs: UserPrefsRepository
    @Inject lateinit var appLock: AppLockManager
    @Inject lateinit var widgets: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        appLock.start(ProcessLifecycleOwner.get().lifecycleScope)
        widgets.start(ProcessLifecycleOwner.get().lifecycleScope)
        MonthStartBudgetWorker.schedule(this)
        RecurringWorker.schedule(this)
        // The daily reminder depends on the chosen time, so read settings first.
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val n = prefs.notifications()
            if (n.dailyReminder) DailyReminderWorker.schedule(this@MoneyRecordsApp, n.reminderHour, n.reminderMinute)
            val b = prefs.observeBackup().first()
            if (b.autoEnabled && b.folderUri != null) AutoBackupWorker.schedule(this@MoneyRecordsApp)
        }
    }
}
