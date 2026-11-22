package com.moneyrecords.app.domain.repository

import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.lock.AppLockPrefs
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Last choices used when adding a record, so the next one is pre-filled. */
data class RecordDefaults(
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
)

/** Floating calculator bubble settings. Position is a fraction (0..1) of the screen. */
data class CalculatorPrefs(
    val enabled: Boolean = true,
    val x: Float = 1f,
    val y: Float = 0.62f,
)

/** Which notifications are on, and when the daily reminder fires. */
data class NotificationPrefs(
    val recurringDue: Boolean = true,
    val upcomingBills: Boolean = true,
    val dailyReminder: Boolean = true,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val budgetAlerts: Boolean = true,
    val monthStartBudgets: Boolean = true,
)

interface UserPrefsRepository {
    suspend fun recordDefaults(kind: RecordKind): RecordDefaults
    suspend fun rememberRecordDefaults(kind: RecordKind, defaults: RecordDefaults)

    /** The month we last asked "keep last month's budgets?" for (null = never). */
    suspend fun lastBudgetPrompt(): YearMonth?
    suspend fun setLastBudgetPrompt(month: YearMonth)

    fun observeNotifications(): Flow<NotificationPrefs>
    suspend fun notifications(): NotificationPrefs
    suspend fun updateNotifications(change: (NotificationPrefs) -> NotificationPrefs)

    /**
     * Remembers notifications already sent (e.g. "due:12:2026-12-01", "budget:…:202612:80") so they
     * aren't repeated. Returns true if [key] was new (and is now recorded).
     */
    suspend fun markSent(key: String): Boolean

    fun observeAppLock(): Flow<AppLockPrefs>
    suspend fun updateAppLock(change: (AppLockPrefs) -> AppLockPrefs)

    fun observeCalculator(): Flow<CalculatorPrefs>
    suspend fun setCalculatorEnabled(enabled: Boolean)
    suspend fun setCalculatorPosition(x: Float, y: Float)
}
