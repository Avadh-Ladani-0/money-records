package com.moneyrecords.app.detect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.detectionPrefs: DataStore<Preferences> by preferencesDataStore(name = "detection_prefs")

/**
 * Auto-detect settings.
 * - [appLinks]: the accounts linked in each payment app (many-to-many: Google Pay ↔ SBI, BOB; SBI ↔
 *   Google Pay, PhonePe). UPI Lite isn't listed here — it's an account of its own (see Account.liteApp).
 * - [appUsual]: the account an app usually pays from, when nothing else tells.
 * - [smsBanks]: bank SMS key ("sms:SBI") → account, learnt from messages that carry the digits.
 */
data class DetectionPrefs(
    val sms: Boolean = false,
    val notifications: Boolean = false,
    val appLinks: Map<String, Set<Long>> = emptyMap(),
    val appUsual: Map<String, Long> = emptyMap(),
    val smsBanks: Map<String, Long> = emptyMap(),
)

@Singleton
class DetectionSettings @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.detectionPrefs
    private val sms = booleanPreferencesKey("sms")
    private val notifications = booleanPreferencesKey("notifications")
    private val links = stringSetPreferencesKey("app_links")      // "pkg=id"
    private val usual = stringSetPreferencesKey("app_usual")      // "pkg=id"
    private val banks = stringSetPreferencesKey("sms_banks")      // "sms:SBI=id"

    private fun Set<String>?.pairs(): List<Pair<String, Long>> = orEmpty().mapNotNull { e ->
        val i = e.lastIndexOf('=')
        if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
    }

    val flow: Flow<DetectionPrefs> = store.data.map { p ->
        DetectionPrefs(
            sms = p[sms] ?: false,
            notifications = p[notifications] ?: false,
            appLinks = p[links].pairs().groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() },
            appUsual = p[usual].pairs().toMap(),
            smsBanks = p[banks].pairs().toMap(),
        )
    }

    suspend fun current() = flow.first()

    suspend fun setSms(on: Boolean) = store.edit { it[sms] = on }
    suspend fun setNotifications(on: Boolean) = store.edit { it[notifications] = on }

    /** Links or unlinks [accountId] in app [pkg]; the usual account is dropped if it's unlinked. */
    suspend fun setLinked(pkg: String, accountId: Long, linked: Boolean) = store.edit { p ->
        val entry = "$pkg=$accountId"
        p[links] = if (linked) p[links].orEmpty() + entry else p[links].orEmpty() - entry
        if (!linked) p[usual] = p[usual].orEmpty() - entry
    }

    suspend fun setUsual(pkg: String, accountId: Long) = store.edit { p ->
        p[usual] = p[usual].orEmpty().filterNot { it.startsWith("$pkg=") }.toSet() + "$pkg=$accountId"
        p[links] = p[links].orEmpty() + "$pkg=$accountId"
    }

    suspend fun learnSmsBank(key: String, accountId: Long) = store.edit { p ->
        p[banks] = p[banks].orEmpty().filterNot { it.startsWith("$key=") }.toSet() + "$key=$accountId"
    }
}
