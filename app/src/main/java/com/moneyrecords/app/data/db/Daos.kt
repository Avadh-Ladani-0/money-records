package com.moneyrecords.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneyrecords.app.domain.model.BudgetScope
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
        WHERE (:includeArchived OR a.archived = 0) AND a.system = 0
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

    @Query("SELECT * FROM accounts WHERE system = 1 LIMIT 1")
    suspend fun systemAccount(): AccountEntity?

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM records WHERE accountId = :id OR toAccountId = :id)
            OR EXISTS(SELECT 1 FROM recurring_rules WHERE accountId = :id OR toAccountId = :id)
            OR EXISTS(SELECT 1 FROM budgets WHERE accountId = :id)
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
     * Records newest first with display names. Every filter is optional (null / "all" flag = no filter).
     * [query] matches note, category or account name; LIKE wildcards in it must be escaped with '\'.
     */
    @Query(
        """
        SELECT r.*, a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.iconKey AS categoryIconKey,
               CASE WHEN r.splitTotal IS NULL THEN 0 ELSE
                   COALESCE((SELECT SUM(l.amount) FROM records l WHERE l.splitOf = r.id), 0)
                 - COALESCE((SELECT SUM(p.amount) FROM records p WHERE p.splitOf IN (SELECT id FROM records WHERE splitOf = r.id)), 0)
               END AS splitOutstanding,
               CASE WHEN r.kind = 'EXPENSE' AND r.splitOf IS NOT NULL
                    THEN COALESCE((SELECT SUM(p.amount) FROM records p WHERE p.splitOf = r.id AND p.awaitingMove = 1), 0)
                    ELSE 0 END AS splitWaiting
        FROM records r
        JOIN accounts a ON a.id = r.accountId
        LEFT JOIN accounts ta ON ta.id = r.toAccountId
        LEFT JOIN categories c ON c.id = r.categoryId
        -- A bill where your share is ₹0 is only an anchor for its parts; the parts are the rows.
        WHERE NOT (r.splitTotal IS NOT NULL AND r.amount = 0)
          AND (:allAccounts OR r.accountId IN (:accountIds) OR r.toAccountId IN (:accountIds))
          AND (:allCategories OR r.categoryId IN (:categoryIds) OR c.parentId IN (:categoryIds))
          AND (:kind IS NULL OR r.kind = :kind)
          AND (:from IS NULL OR r.occurredAt >= :from)
          AND (:to IS NULL OR r.occurredAt < :to)
          AND (:query IS NULL
               OR r.note LIKE '%' || :query || '%' ESCAPE '\'
               OR c.name LIKE '%' || :query || '%' ESCAPE '\'
               OR a.name LIKE '%' || :query || '%' ESCAPE '\'
               OR r.person LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY r.occurredAt DESC, r.id DESC
        LIMIT :limit
        """,
    )
    fun observe(
        allAccounts: Boolean,
        accountIds: List<Long>,
        allCategories: Boolean,
        categoryIds: List<Long>,
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

    /** Money leaving each account in a range: expenses paid from it + transfers out of it. */
    @Query(
        """
        SELECT accountId, SUM(amount) AS total FROM records
        WHERE kind IN ('EXPENSE', 'TRANSFER') AND occurredAt >= :from AND occurredAt < :to
          -- Paying for friends isn't your spending: their parts go to the hidden To-collect account.
          AND (toAccountId IS NULL OR toAccountId NOT IN (SELECT id FROM accounts WHERE system = 1))
          AND accountId NOT IN (SELECT id FROM accounts WHERE system = 1)
        GROUP BY accountId
        """,
    )
    fun observeOutflowByAccount(from: Long, to: Long): Flow<List<AccountSpend>>

    /**
     * Records that may be counted back against a budget in a range: income (into [accountId] when
     * given) and, for an account, transfers into it. Newest first, with display names.
     */
    @Query(
        """
        SELECT r.*, a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.iconKey AS categoryIconKey, 0 AS splitOutstanding, 0 AS splitWaiting
        FROM records r
        JOIN accounts a ON a.id = r.accountId
        LEFT JOIN accounts ta ON ta.id = r.toAccountId
        LEFT JOIN categories c ON c.id = r.categoryId
        WHERE r.occurredAt >= :from AND r.occurredAt < :to
          AND ((r.kind = 'INCOME' AND (:accountId IS NULL OR r.accountId = :accountId))
               OR (:accountId IS NOT NULL AND r.kind = 'TRANSFER' AND r.toAccountId = :accountId))
        ORDER BY r.occurredAt DESC, r.id DESC
        """,
    )
    suspend fun offsetCandidates(accountId: Long?, from: Long, to: Long): List<RecordRow>

    @Query("SELECT COUNT(*) FROM records WHERE occurredAt >= :from AND occurredAt < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): RecordEntity?

    @Insert suspend fun insert(record: RecordEntity): Long
    @Update suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)

    // ── Split payments ──

    @Query("SELECT * FROM records WHERE splitOf = :id ORDER BY id")
    suspend fun children(id: Long): List<RecordEntity>

    /** Deletes a record's split parts and their repayments (call before deleting the record). */
    @Query("DELETE FROM records WHERE splitOf IN (SELECT id FROM records WHERE splitOf = :id)")
    suspend fun deleteGrandchildren(id: Long)

    @Query("DELETE FROM records WHERE splitOf = :id")
    suspend fun deleteChildren(id: Long)

    /** Open to-collect parts: what's left on each, newest bill first, with the bill's details. */
    @Query(
        """
        SELECT l.id AS legId, l.person AS person, l.accountId AS payerId, pa.name AS payerName,
               e.id AS expenseId, e.note AS note, e.occurredAt AS occurredAt, c.name AS categoryName,
               c.iconKey AS categoryIconKey, e.splitTotal AS billTotal,
               l.amount - COALESCE((SELECT SUM(p.amount) FROM records p WHERE p.splitOf = l.id), 0) AS outstanding,
               COALESCE((SELECT SUM(p.amount) FROM records p WHERE p.splitOf = l.id AND p.awaitingMove = 1), 0) AS waiting
        FROM records l
        JOIN records e ON e.id = l.splitOf
        JOIN accounts pa ON pa.id = l.accountId
        LEFT JOIN categories c ON c.id = e.categoryId
        WHERE l.kind = 'EXPENSE' AND l.splitOf IS NOT NULL
        ORDER BY e.occurredAt DESC, l.id
        """,
    )
    fun observeOpenParts(): Flow<List<OpenPartRow>>

    /** Repayments waiting to be moved, per account they should go back to (the original payer). */
    @Query(
        """
        SELECT l.accountId AS payerId, pa.name AS payerName, p.toAccountId AS heldIn, ha.name AS heldInName,
               SUM(p.amount) AS total, COUNT(*) AS count
        FROM records p
        JOIN records l ON l.id = p.splitOf
        JOIN accounts pa ON pa.id = l.accountId
        JOIN accounts ha ON ha.id = p.toAccountId
        WHERE p.awaitingMove = 1
        GROUP BY l.accountId, p.toAccountId
        ORDER BY total DESC
        """,
    )
    fun observeAwaitingMove(): Flow<List<AwaitingMoveRow>>

    @Query(
        """
        SELECT p.* FROM records p JOIN records l ON l.id = p.splitOf
        WHERE p.awaitingMove = 1 AND l.accountId = :payerId AND p.toAccountId = :heldInId
        ORDER BY p.occurredAt, p.id
        """,
    )
    suspend fun awaitingFor(payerId: Long, heldInId: Long): List<RecordEntity>

    /** Waiting repayments for [recordId] (a part) or for any part of [recordId] (a bill). */
    @Query(
        """
        SELECT * FROM records WHERE awaitingMove = 1
          AND (splitOf = :recordId OR splitOf IN (SELECT id FROM records WHERE splitOf = :recordId))
        """,
    )
    suspend fun waitingFor(recordId: Long): List<RecordEntity>

    @Query("UPDATE records SET categoryId = :categoryId, occurredAt = :occurredAt, note = :note, updatedAt = :now WHERE splitOf = :expenseId AND kind = 'EXPENSE'")
    suspend fun updatePartDetails(expenseId: Long, categoryId: Long?, occurredAt: Long, note: String, now: Long)

    @Query("SELECT DISTINCT person FROM records WHERE person IS NOT NULL AND person != '' ORDER BY person COLLATE NOCASE")
    fun observePeople(): Flow<List<String>>

    @Query("DELETE FROM records WHERE splitOf = :legId")
    suspend fun deleteGrandchildrenOfLeg(legId: Long)

    @Query("UPDATE records SET amount = :amount WHERE id = :id")
    suspend fun setAmount(id: Long, amount: Long)
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, nextRunDate")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: Long): RecurringRuleEntity?

    @Query(
        """
        SELECT r.*, a.name AS accountName, ta.name AS toAccountName,
               c.name AS categoryName, c.iconKey AS categoryIconKey
        FROM recurring_rules r
        JOIN accounts a ON a.id = r.accountId
        LEFT JOIN accounts ta ON ta.id = r.toAccountId
        LEFT JOIN categories c ON c.id = r.categoryId
        ORDER BY r.active DESC, r.nextRunDate
        """,
    )
    fun observeAllWithNames(): Flow<List<RecurringRuleRow>>

    @Query("UPDATE recurring_rules SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Insert suspend fun insert(rule: RecurringRuleEntity): Long
    @Update suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetDao {
    /**
     * Budgets in effect for [yearMonth]: for each (scope, category) the row with the latest month
     * on or before it. Stop markers (limit 0) hide the budget. `IS` matches null categories.
     */
    @Query(
        """
        SELECT b.* FROM budgets b
        WHERE b.yearMonth = (
            SELECT MAX(b2.yearMonth) FROM budgets b2
            WHERE b2.scope = b.scope AND b2.categoryId IS b.categoryId AND b2.accountId IS b.accountId
              AND b2.yearMonth <= :yearMonth
        )
        AND b.limitAmount > 0
        """,
    )
    fun observeEffective(yearMonth: Int): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets WHERE scope = :scope AND categoryId IS :categoryId AND accountId IS :accountId
          AND yearMonth = :yearMonth LIMIT 1
        """,
    )
    suspend fun find(scope: BudgetScope, categoryId: Long?, accountId: Long?, yearMonth: Int): BudgetEntity?

    /** The most recent row for this budget before [yearMonth], if any. */
    @Query(
        """
        SELECT * FROM budgets WHERE scope = :scope AND categoryId IS :categoryId AND accountId IS :accountId
          AND yearMonth < :yearMonth
        ORDER BY yearMonth DESC LIMIT 1
        """,
    )
    suspend fun latestBefore(scope: BudgetScope, categoryId: Long?, accountId: Long?, yearMonth: Int): BudgetEntity?

    @Insert suspend fun insert(budget: BudgetEntity): Long
    @Update suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetOffsetDao {
    /** Counted-back amounts for every budget line, for records dated in [from, to). */
    @Query(
        """
        SELECT o.scope, o.categoryId, o.accountId, r.amount FROM budget_offsets o
        JOIN records r ON r.id = o.recordId
        WHERE r.occurredAt >= :from AND r.occurredAt < :to
        """,
    )
    fun observeInRange(from: Long, to: Long): Flow<List<OffsetRow>>

    @Query(
        """
        SELECT o.recordId FROM budget_offsets o JOIN records r ON r.id = o.recordId
        WHERE o.scope = :scope AND o.categoryId IS :categoryId AND o.accountId IS :accountId
          AND r.occurredAt >= :from AND r.occurredAt < :to
        """,
    )
    suspend fun selectedIds(scope: BudgetScope, categoryId: Long?, accountId: Long?, from: Long, to: Long): List<Long>

    @Query(
        """
        DELETE FROM budget_offsets
        WHERE scope = :scope AND categoryId IS :categoryId AND accountId IS :accountId
          AND recordId IN (SELECT id FROM records WHERE occurredAt >= :from AND occurredAt < :to)
        """,
    )
    suspend fun clear(scope: BudgetScope, categoryId: Long?, accountId: Long?, from: Long, to: Long)

    @Insert suspend fun insertAll(offsets: List<BudgetOffsetEntity>)
}

/** Whole-table reads and writes for backup & restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts ORDER BY id") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM recurring_rules ORDER BY id") suspend fun recurringRules(): List<RecurringRuleEntity>
    @Query("SELECT * FROM records ORDER BY id") suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM budgets ORDER BY id") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM budget_offsets ORDER BY id") suspend fun budgetOffsets(): List<BudgetOffsetEntity>

    // Children before parents, so foreign keys are never left dangling.
    @Query("DELETE FROM budget_offsets") suspend fun clearBudgetOffsets()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM recurring_rules") suspend fun clearRecurringRules()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    @Insert suspend fun insertAccounts(rows: List<AccountEntity>)
    @Insert suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert suspend fun insertRecurringRules(rows: List<RecurringRuleEntity>)
    @Insert suspend fun insertRecords(rows: List<RecordEntity>)
    @Insert suspend fun insertBudgets(rows: List<BudgetEntity>)
    @Insert suspend fun insertBudgetOffsets(rows: List<BudgetOffsetEntity>)
}
