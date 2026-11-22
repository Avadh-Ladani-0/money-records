package com.moneyrecords.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.work.DailyReminderWorker
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

    override fun onCreate() {
        super.onCreate()
        MonthStartBudgetWorker.schedule(this)
        RecurringWorker.schedule(this)
        // The daily reminder depends on the chosen time, so read settings first.
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            val n = prefs.notifications()
            if (n.dailyReminder) DailyReminderWorker.schedule(this@MoneyRecordsApp, n.reminderHour, n.reminderMinute)
        }
    }
}
