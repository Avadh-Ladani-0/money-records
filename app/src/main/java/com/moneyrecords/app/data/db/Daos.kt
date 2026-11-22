package com.moneyrecords.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /**
     * Balance = opening + income − expense − transfers out + transfers in.
     * Correlated subqueries use the records(accountId)/(toAccountId) indices.
     */
    @Query(
        """
        SELECT a.*, a.openingBalance
            + COALESCE((SELECT SUM(r.amount) FROM records r WHERE r.accountId = a.id AND r.kind = 'INCOME'), 0)
            - COALESCE((SELECT SUM(r.amount) FROM records r WHERE r.accountId = a.id AND r.kind IN ('EXPENSE', 'TRANSFER')), 0)
            + COALESCE((SELECT SUM(r.amount) FROM records r WHERE r.toAccountId = a.id AND r.kind = 'TRANSFER'), 0)
            AS balance
        FROM accounts a
        WHERE (:includeArchived OR a.archived = 0)
        ORDER BY a.sortOrder, a.name COLLATE NOCASE
        """,
    )
    fun observeWithBalance(includeArchived: Boolean): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert suspend fun insert(account: AccountEntity): Long
    @Update suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts")
    suspend fun nextSortOrder(): Int

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM records WHERE accountId = :id OR toAccountId = :id)
            OR EXISTS(SELECT 1 FROM recurring_rules WHERE accountId = :id OR toAccountId = :id)
        """,
    )
    suspend fun isUsed(id: Long): Boolean
}

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE kind = :kind AND (:includeArchived OR archived = 0)
        ORDER BY sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeByKind(kind: CategoryKind, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert suspend fun insert(category: CategoryEntity): Long
    @Update suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id OR parentId = :id")
    suspend fun setArchivedWithChildren(id: Long, archived: Boolean)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE kind = :kind")
    suspend fun nextSortOrder(kind: CategoryKind): Int

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM records WHERE categoryId = :id)
            OR EXISTS(SELECT 1 FROM recurring_rules WHERE categoryId = :id)
            OR EXISTS(SELECT 1 FROM budgets WHERE categoryId = :id)
            OR EXISTS(SELECT 1 FROM categories WHERE parentId = :id)
        """,
    )
    suspend fun isUsed(id: Long): Boolean
}

@Dao
interface RecordDao {
    /**
     * Records newest first with display names. Every filter is optional (null = no filter).
     * [query] matches note, category or account name; LIKE wildcards in it must be escaped with '\'.
     */
    @Query(
        """
        SELECT r.*, a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.iconKey AS categoryIconKey
        FROM records r
        JOIN accounts a ON a.id = r.accountId
        LEFT JOIN accounts ta ON ta.id = r.toAccountId
        LEFT JOIN categories c ON c.id = r.categoryId
        WHERE (:accountId IS NULL OR r.accountId = :accountId OR r.toAccountId = :accountId)
          AND (:categoryId IS NULL OR r.categoryId = :categoryId OR c.parentId = :categoryId)
          AND (:kind IS NULL OR r.kind = :kind)
          AND (:from IS NULL OR r.occurredAt >= :from)
          AND (:to IS NULL OR r.occurredAt < :to)
          AND (:query IS NULL
               OR r.note LIKE '%' || :query || '%' ESCAPE '\'
               OR c.name LIKE '%' || :query || '%' ESCAPE '\'
               OR a.name LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY r.occurredAt DESC, r.id DESC
        LIMIT :limit
        """,
    )
    fun observe(
        accountId: Long?,
        categoryId: Long?,
        kind: RecordKind?,
        from: Long?,
        to: Long?,
        query: String?,
        limit: Int,
    ): Flow<List<RecordRow>>

    /** Income/expense totals in a range; transfers are movements, not income or spending. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amount END), 0) AS income,
               COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amount END), 0) AS expense
        FROM records WHERE occurredAt >= :from AND occurredAt < :to
        """,
    )
    fun observeTotals(from: Long, to: Long): Flow<TotalsRow>

    /** Expense per top-level category (subcategory spend rolls up into its parent). */
    @Query(
        """
        SELECT COALESCE(c.parentId, c.id) AS categoryId, SUM(r.amount) AS total
        FROM records r JOIN categories c ON c.id = r.categoryId
        WHERE r.kind = 'EXPENSE' AND r.occurredAt >= :from AND r.occurredAt < :to
        GROUP BY COALESCE(c.parentId, c.id)
        """,
    )
    fun observeExpenseByCategory(from: Long, to: Long): Flow<List<CategorySpend>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): RecordEntity?

    @Insert suspend fun insert(record: RecordEntity): Long
    @Update suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, nextRunDate")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE active = 1 AND nextRunDate <= :epochDay ORDER BY nextRunDate")
    suspend fun dueOn(epochDay: Long): List<RecurringRuleEntity>

    @Insert suspend fun insert(rule: RecurringRuleEntity): Long
    @Update suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun observeForMonth(yearMonth: Int): Flow<List<BudgetEntity>>

    /** `IS` so that a null category (overall budget) matches too. */
    @Query("SELECT * FROM budgets WHERE categoryId IS :categoryId AND yearMonth = :yearMonth LIMIT 1")
    suspend fun find(categoryId: Long?, yearMonth: Int): BudgetEntity?

    @Insert suspend fun insert(budget: BudgetEntity): Long
    @Update suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)
}
