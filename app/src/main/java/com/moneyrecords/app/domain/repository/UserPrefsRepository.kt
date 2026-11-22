package com.moneyrecords.app.domain.repository

import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.coroutines.flow.Flow

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

interface UserPrefsRepository {
    suspend fun recordDefaults(kind: RecordKind): RecordDefaults
    suspend fun rememberRecordDefaults(kind: RecordKind, defaults: RecordDefaults)

    fun observeCalculator(): Flow<CalculatorPrefs>
    suspend fun setCalculatorEnabled(enabled: Boolean)
    suspend fun setCalculatorPosition(x: Float, y: Float)
}
