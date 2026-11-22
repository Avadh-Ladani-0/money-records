package com.moneyrecords.app.di

import com.moneyrecords.app.notify.BudgetAlertChecker
import com.moneyrecords.app.notify.BudgetAlertNotifier
import com.moneyrecords.app.notify.BudgetAlertPoster
import com.moneyrecords.app.notify.BudgetAlerts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds abstract fun budgetAlerts(impl: BudgetAlertChecker): BudgetAlerts
    @Binds abstract fun budgetAlertPoster(impl: BudgetAlertNotifier): BudgetAlertPoster
}
