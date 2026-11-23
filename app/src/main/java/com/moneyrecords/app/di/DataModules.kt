package com.moneyrecords.app.di

import android.content.Context
import androidx.room.Room
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.MIGRATION_1_2
import com.moneyrecords.app.data.db.MIGRATION_2_3
import com.moneyrecords.app.data.db.MIGRATION_3_4
import com.moneyrecords.app.data.db.MIGRATION_4_5
import com.moneyrecords.app.data.db.MIGRATION_5_6
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.prefs.DataStoreUserPrefs
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomBudgetRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.data.repository.RoomRecurringRepository
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): MoneyRecordsDatabase =
        Room.databaseBuilder(context, MoneyRecordsDatabase::class.java, MoneyRecordsDatabase.NAME)
            .addCallback(SeedCallback())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    /** Injected so tests can pin "now" and the time zone. */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun accounts(impl: RoomAccountRepository): AccountRepository
    @Binds abstract fun categories(impl: RoomCategoryRepository): CategoryRepository
    @Binds abstract fun records(impl: RoomRecordRepository): RecordRepository
    @Binds abstract fun recurring(impl: RoomRecurringRepository): RecurringRepository
    @Binds abstract fun budgets(impl: RoomBudgetRepository): BudgetRepository
    @Binds abstract fun detection(impl: com.moneyrecords.app.data.repository.RoomDetectionRepository): com.moneyrecords.app.domain.detect.DetectionRepository
    @Binds abstract fun detectedPrompts(impl: com.moneyrecords.app.notify.DetectedNotifier): com.moneyrecords.app.domain.detect.DetectedPrompts
    @Binds abstract fun splits(impl: com.moneyrecords.app.data.repository.RoomSplitRepository): com.moneyrecords.app.domain.repository.SplitRepository
    @Binds abstract fun userPrefs(impl: DataStoreUserPrefs): UserPrefsRepository
}
