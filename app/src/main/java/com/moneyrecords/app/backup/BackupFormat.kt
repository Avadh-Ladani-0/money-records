package com.moneyrecords.app.backup

import com.moneyrecords.app.data.db.AccountEntity
import com.moneyrecords.app.data.db.BudgetEntity
import com.moneyrecords.app.data.db.BudgetOffsetEntity
import com.moneyrecords.app.data.db.CategoryEntity
import com.moneyrecords.app.data.db.RecordEntity
import com.moneyrecords.app.data.db.RecurringRuleEntity
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.serialization.Serializable

/*
 * The backup file: one JSON document with every table, ids kept, so links (subcategories, combined
 * categories, records made by a repeat, counted-back records) survive a restore exactly.
 * Enums are written as names. Bump FORMAT_VERSION only for changes older apps can't read.
 */

const val BACKUP_FORMAT = "moneyrecords-backup"
const val FORMAT_VERSION = 1

@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val version: Int = FORMAT_VERSION,
    val appVersion: String,
    val dbVersion: Int,
    /** Epoch millis. */
    val createdAt: Long,
    val data: BackupData,
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class BackupData(
    val accounts: List<AccountRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val recurringRules: List<RecurringRuleRow> = emptyList(),
    val records: List<RecordRow> = emptyList(),
    val budgets: List<BudgetRow> = emptyList(),
    val budgetOffsets: List<BudgetOffsetRow> = emptyList(),
)

/** What's in a backup — shown before restoring. */
data class BackupCounts(
    val accounts: Int,
    val categories: Int,
    val records: Int,
    val recurring: Int,
    val budgets: Int,
) {
    val summary: String
        get() = listOf(
            records to ("record" to "records"),
            accounts to ("account" to "accounts"),
            budgets to ("budget" to "budgets"),
            recurring to ("recurring item" to "recurring items"),
            categories to ("category" to "categories"),
        ).joinToString(" · ") { (n, words) -> "$n ${if (n == 1) words.first else words.second}" }
}

fun BackupData.counts() = BackupCounts(
    accounts = accounts.size,
    categories = categories.size,
    records = records.size,
    recurring = recurringRules.size,
    // Stop markers (limit 0) aren't budgets the user would count.
    budgets = budgets.count { it.limitAmount > 0 },
)

@Serializable
data class BackupSettings(
    val theme: String? = null,
    val calculatorEnabled: Boolean? = null,
    val relockAfterSeconds: Int? = null,
    val hideInRecents: Boolean? = null,
    val notifyRecurringDue: Boolean? = null,
    val notifyUpcomingBills: Boolean? = null,
    val notifyDailyReminder: Boolean? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val notifyBudgetAlerts: Boolean? = null,
    val notifyMonthStart: Boolean? = null,
)

@Serializable data class AccountRow(
    val id: Long, val name: String, val type: String, val openingBalance: Long, val iconKey: String,
    val archived: Boolean, val sortOrder: Int, val createdAt: Long,
)

@Serializable data class CategoryRow(
    val id: Long, val name: String, val kind: String, val parentId: Long? = null, val iconKey: String,
    val archived: Boolean, val sortOrder: Int,
)

@Serializable data class RecurringRuleRow(
    val id: Long, val kind: String, val amount: Long, val accountId: Long, val toAccountId: Long? = null,
    val categoryId: Long? = null, val note: String, val frequency: String, val interval: Int,
    val startDate: Long, val endDate: Long? = null, val nextRunDate: Long, val active: Boolean,
)

@Serializable data class RecordRow(
    val id: Long, val kind: String, val amount: Long, val accountId: Long, val toAccountId: Long? = null,
    val categoryId: Long? = null, val occurredAt: Long, val note: String, val recurringRuleId: Long? = null,
    val createdAt: Long, val updatedAt: Long,
)

@Serializable data class BudgetRow(
    val id: Long, val scope: String, val categoryId: Long? = null, val accountId: Long? = null,
    val yearMonth: Int, val limitAmount: Long,
)

@Serializable data class BudgetOffsetRow(
    val id: Long, val scope: String, val categoryId: Long? = null, val accountId: Long? = null, val recordId: Long,
)

// ── Entity ↔ row ────────────────────────────────────────────────────────────────────────────────

fun AccountEntity.toRow() = AccountRow(id, name, type.name, openingBalance, iconKey, archived, sortOrder, createdAt)
fun AccountRow.toEntity() = AccountEntity(id, name, AccountType.valueOf(type), openingBalance, iconKey, archived, sortOrder, createdAt)

fun CategoryEntity.toRow() = CategoryRow(id, name, kind.name, parentId, iconKey, archived, sortOrder)
fun CategoryRow.toEntity() = CategoryEntity(id, name, CategoryKind.valueOf(kind), parentId, iconKey, archived, sortOrder)

fun RecurringRuleEntity.toRow() = RecurringRuleRow(
    id, kind.name, amount, accountId, toAccountId, categoryId, note, frequency.name, interval, startDate, endDate, nextRunDate, active,
)
fun RecurringRuleRow.toEntity() = RecurringRuleEntity(
    id, RecordKind.valueOf(kind), amount, accountId, toAccountId, categoryId, note, Frequency.valueOf(frequency), interval,
    startDate, endDate, nextRunDate, active,
)

fun RecordEntity.toRow() = RecordRow(id, kind.name, amount, accountId, toAccountId, categoryId, occurredAt, note, recurringRuleId, createdAt, updatedAt)
fun RecordRow.toEntity() = RecordEntity(
    id, RecordKind.valueOf(kind), amount, accountId, toAccountId, categoryId, occurredAt, note, recurringRuleId, createdAt, updatedAt,
)

fun BudgetEntity.toRow() = BudgetRow(id, scope.name, categoryId, accountId, yearMonth, limitAmount)
fun BudgetRow.toEntity() = BudgetEntity(id, BudgetScope.valueOf(scope), categoryId, accountId, yearMonth, limitAmount)

fun BudgetOffsetEntity.toRow() = BudgetOffsetRow(id, scope.name, categoryId, accountId, recordId)
fun BudgetOffsetRow.toEntity() = BudgetOffsetEntity(id, BudgetScope.valueOf(scope), categoryId, accountId, recordId)

/** Parents before their subcategories, so the self-referencing foreign key is satisfied on insert. */
fun List<CategoryRow>.parentsFirst(): List<CategoryRow> {
    val byId = associateBy { it.id }
    fun depth(row: CategoryRow): Int {
        var d = 0
        var p = row.parentId
        while (p != null && d < 10) { d++; p = byId[p]?.parentId }
        return d
    }
    return sortedWith(compareBy<CategoryRow> { depth(it) }.thenBy { it.id })
}
