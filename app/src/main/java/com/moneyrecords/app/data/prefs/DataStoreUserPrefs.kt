package com.moneyrecords.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.domain.repository.RecordDefaults
import com.moneyrecords.app.domain.repository.UserPrefsRepository
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
}
