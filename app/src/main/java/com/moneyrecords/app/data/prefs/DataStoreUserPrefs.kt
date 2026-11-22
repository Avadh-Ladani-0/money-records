package com.moneyrecords.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.moneyrecords.app.domain.repository.NotificationPrefs
import com.moneyrecords.app.domain.lock.AppLockPrefs
import androidx.datastore.preferences.preferencesDataStore
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.domain.repository.RecordDefaults
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.domain.time.MonthRange
import java.time.YearMonth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class DataStoreUserPrefs @Inject constructor(
    @ApplicationContext context: Context,
) : UserPrefsRepository {
    private val store = context.userPrefs

    private fun accountKey(kind: RecordKind) = longPreferencesKey("last_account_${kind.name}")
    private fun toAccountKey(kind: RecordKind) = longPreferencesKey("last_to_account_${kind.name}")
    private fun categoryKey(kind: RecordKind) = longPreferencesKey("last_category_${kind.name}")
    private val lastBudgetPromptKey = intPreferencesKey("last_budget_prompt")
    private val nRecurring = booleanPreferencesKey("notify_recurring_due")
    private val nUpcoming = booleanPreferencesKey("notify_upcoming_bills")
    private val nDaily = booleanPreferencesKey("notify_daily_reminder")
    private val nHour = intPreferencesKey("notify_reminder_hour")
    private val nMinute = intPreferencesKey("notify_reminder_minute")
    private val nBudgets = booleanPreferencesKey("notify_budget_alerts")
    private val nMonthStart = booleanPreferencesKey("notify_month_start")
    private val sentKeys = stringSetPreferencesKey("sent_notifications")
    private val lockEnabled = booleanPreferencesKey("lock_enabled")
    private val lockRelock = intPreferencesKey("lock_relock_seconds")
    private val lockHideRecents = booleanPreferencesKey("lock_hide_recents")
    private val calcEnabled = booleanPreferencesKey("calc_enabled")
    private val calcX = floatPreferencesKey("calc_x")
    private val calcY = floatPreferencesKey("calc_y")

    override suspend fun recordDefaults(kind: RecordKind): RecordDefaults {
        val prefs = store.data.first()
        return RecordDefaults(
            accountId = prefs[accountKey(kind)],
            toAccountId = prefs[toAccountKey(kind)],
            categoryId = prefs[categoryKey(kind)],
        )
    }

    override suspend fun rememberRecordDefaults(kind: RecordKind, defaults: RecordDefaults) {
        store.edit { prefs ->
            fun put(key: Preferences.Key<Long>, value: Long?) {
                if (value == null) prefs.remove(key) else prefs[key] = value
            }
            put(accountKey(kind), defaults.accountId)
            put(toAccountKey(kind), defaults.toAccountId)
            put(categoryKey(kind), defaults.categoryId)
        }
    }

    override suspend fun lastBudgetPrompt(): YearMonth? =
        store.data.first()[lastBudgetPromptKey]?.let(MonthRange::fromKey)

    override suspend fun setLastBudgetPrompt(month: YearMonth) {
        store.edit { it[lastBudgetPromptKey] = MonthRange.key(month) }
    }

    private fun Preferences.toNotificationPrefs(): NotificationPrefs {
        val d = NotificationPrefs()
        return NotificationPrefs(
            recurringDue = this[nRecurring] ?: d.recurringDue,
            upcomingBills = this[nUpcoming] ?: d.upcomingBills,
            dailyReminder = this[nDaily] ?: d.dailyReminder,
            reminderHour = this[nHour] ?: d.reminderHour,
            reminderMinute = this[nMinute] ?: d.reminderMinute,
            budgetAlerts = this[nBudgets] ?: d.budgetAlerts,
            monthStartBudgets = this[nMonthStart] ?: d.monthStartBudgets,
        )
    }

    override fun observeNotifications(): Flow<NotificationPrefs> = store.data.map { it.toNotificationPrefs() }

    override suspend fun notifications(): NotificationPrefs = store.data.first().toNotificationPrefs()

    override suspend fun updateNotifications(change: (NotificationPrefs) -> NotificationPrefs) {
        store.edit { prefs ->
            val n = change(prefs.toNotificationPrefs())
            prefs[nRecurring] = n.recurringDue
            prefs[nUpcoming] = n.upcomingBills
            prefs[nDaily] = n.dailyReminder
            prefs[nHour] = n.reminderHour.coerceIn(0, 23)
            prefs[nMinute] = n.reminderMinute.coerceIn(0, 59)
            prefs[nBudgets] = n.budgetAlerts
            prefs[nMonthStart] = n.monthStartBudgets
        }
    }

    override suspend fun markSent(key: String): Boolean {
        var added = false
        store.edit { prefs ->
            val current = prefs[sentKeys].orEmpty()
            if (key !in current) {
                added = true
                // Keep the set small: the newest few hundred keys are plenty to avoid repeats.
                prefs[sentKeys] = (current.toList().takeLast(MAX_SENT_KEYS - 1) + key).toSet()
            }
        }
        return added
    }

    override fun observeAppLock(): Flow<AppLockPrefs> = store.data.map { p ->
        val d = AppLockPrefs()
        AppLockPrefs(
            enabled = p[lockEnabled] ?: d.enabled,
            relockAfterSeconds = p[lockRelock] ?: d.relockAfterSeconds,
            hideInRecents = p[lockHideRecents] ?: d.hideInRecents,
        )
    }

    override suspend fun updateAppLock(change: (AppLockPrefs) -> AppLockPrefs) {
        val current = observeAppLock().first()
        val next = change(current)
        store.edit {
            it[lockEnabled] = next.enabled
            it[lockRelock] = next.relockAfterSeconds.coerceAtLeast(0)
            it[lockHideRecents] = next.hideInRecents
        }
    }

    override fun observeCalculator(): Flow<CalculatorPrefs> = store.data.map { prefs ->
        val default = CalculatorPrefs()
        CalculatorPrefs(
            enabled = prefs[calcEnabled] ?: default.enabled,
            x = prefs[calcX] ?: default.x,
            y = prefs[calcY] ?: default.y,
        )
    }

    override suspend fun setCalculatorEnabled(enabled: Boolean) {
        store.edit { it[calcEnabled] = enabled }
    }

    override suspend fun setCalculatorPosition(x: Float, y: Float) {
        store.edit {
            it[calcX] = x.coerceIn(0f, 1f)
            it[calcY] = y.coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val MAX_SENT_KEYS = 400
    }
}
