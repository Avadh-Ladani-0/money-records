package com.moneyrecords.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind

// Enums are stored as their names (Room's built-in enum converter).
// Foreign keys RESTRICT deletes: used accounts/categories are archived instead (see repositories).

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("nextRunDate")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: RecordKind,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String,
    val frequency: Frequency,
    val interval: Int,
    /** Epoch days. */
    val startDate: Long,
    val endDate: Long? = null,
    val nextRunDate: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecurringRuleEntity::class, parentColumns = ["id"], childColumns = ["recurringRuleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("occurredAt"), Index("recurringRuleId")],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: RecordKind,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** Epoch millis. */
    val occurredAt: Long,
    val note: String,
    val recurringRuleId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["categoryId", "yearMonth"], unique = true), Index("accountId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** OVERALL | CATEGORY | REST (added in DB version 2). */
    @ColumnInfo(defaultValue = "CATEGORY") val scope: BudgetScope = BudgetScope.CATEGORY,
    val categoryId: Long?,
    /** Only for ACCOUNT budgets (added in DB version 3). */
    val accountId: Long? = null,
    /** e.g. 202611. */
    val yearMonth: Int,
    /** Paise; 0 = stop marker ("no budget from this month on"). ("limit" is an SQL keyword.) */
    val limitAmount: Long,
)

/**
 * A record hand-picked to count back against a budget line (scope + category/account), in the month
 * of the record's date. Deleting the record removes the selection (DB version 3).
 */
@Entity(
    tableName = "budget_offsets",
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordId"), Index(value = ["scope", "categoryId", "accountId"])],
)
data class BudgetOffsetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: BudgetScope,
    val categoryId: Long?,
    val accountId: Long?,
    val recordId: Long,
)

// --- Query result shapes ---

data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    val balance: Long,
)

data class RecordRow(
    @Embedded val record: RecordEntity,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
)

data class TotalsRow(val income: Long, val expense: Long)

data class CategorySpend(val categoryId: Long, val total: Long)

data class AccountSpend(val accountId: Long, val total: Long)

/** A counted-back record's amount, keyed by the budget line it belongs to. */
data class OffsetRow(val scope: BudgetScope, val categoryId: Long?, val accountId: Long?, val amount: Long)
