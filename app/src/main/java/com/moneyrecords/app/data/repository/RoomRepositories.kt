package com.moneyrecords.app.data.repository

import androidx.room.withTransaction
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.mapper.toDomain
import com.moneyrecords.app.data.mapper.toEntity
import com.moneyrecords.app.data.mapper.toTree
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetProgress
import kotlinx.coroutines.flow.first
import com.moneyrecords.app.data.db.CategoryEntity
import com.moneyrecords.app.data.db.BudgetEntity
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.model.BudgetSuggestion
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.domain.time.MonthRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val clock: Clock,
) : AccountRepository {
    private val dao = db.accountDao()

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        dao.observeWithBalance(includeArchived).map { rows -> rows.map { it.toDomain() } }

    override fun observeTotalBalance(): Flow<Long> =
        observeAccounts(includeArchived = false).map { accounts -> accounts.sumOf { it.balance } }

    override suspend fun get(id: Long): Account? = dao.get(id)?.toDomain()

    override suspend fun save(account: Account): Long {
        require(account.name.isNotBlank()) { "Account name is required" }
        return db.withTransaction {
            if (account.id == 0L) {
                dao.insert(account.copy(sortOrder = dao.nextSortOrder()).toEntity(createdAt = clock.millis()))
            } else {
                val existing = requireNotNull(dao.get(account.id)) { "Account ${account.id} not found" }
                dao.update(account.toEntity(createdAt = existing.createdAt))
                account.id
            }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    override suspend fun deleteOrArchive(id: Long): DeleteOutcome = db.withTransaction {
        if (dao.isUsed(id)) {
            dao.setArchived(id, true)
            DeleteOutcome.ARCHIVED
        } else {
            dao.delete(id)
            DeleteOutcome.DELETED
        }
    }
}

class RoomCategoryRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
) : CategoryRepository {
    private val dao = db.categoryDao()

    override fun observeTree(kind: CategoryKind, includeArchived: Boolean): Flow<List<Category>> =
        dao.observeByKind(kind, includeArchived).map { it.toTree() }

    override suspend fun get(id: Long): Category? = dao.get(id)?.toDomain()

    override suspend fun save(category: Category): Long {
        require(category.name.isNotBlank()) { "Category name is required" }
        return db.withTransaction {
            category.parentId?.let { parentId ->
                val parent = requireNotNull(dao.get(parentId)) { "Parent category not found" }
                require(parent.parentId == null) { "Subcategories can't have subcategories" }
                require(parent.kind == category.kind) { "Subcategory must match its parent's type" }
                require(parentId != category.id) { "A category can't be its own parent" }
            }
            if (category.id == 0L) {
                dao.insert(category.copy(sortOrder = dao.nextSortOrder(category.kind)).toEntity())
            } else {
                dao.update(category.toEntity())
                category.id
            }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchivedWithChildren(id, archived)

    override suspend fun combine(firstId: Long, secondId: Long, name: String): Long = db.withTransaction {
        require(firstId != secondId) { "Pick two different categories" }
        val first = requireNotNull(dao.get(firstId)) { "Category not found" }
        val second = requireNotNull(dao.get(secondId)) { "Category not found" }
        require(first.parentId == null && second.parentId == null) { "Only main categories can be combined" }
        require(!ComboIcon.isCombo(first.iconKey) && !ComboIcon.isCombo(second.iconKey)) { "Combined categories can't be combined again" }
        require(first.kind == second.kind) { "Both categories must be the same type" }
        require(!first.archived && !second.archived) { "Archived categories can't be combined" }
        save(
            Category(
                name = name.ifBlank { "${first.name}-${second.name}" },
                kind = first.kind,
                parentId = first.id,
                iconKey = ComboIcon.key(first.iconKey, second.iconKey),
            ),
        )
    }

    override suspend fun deleteOrArchive(id: Long): DeleteOutcome = db.withTransaction {
        if (dao.isUsed(id)) {
            dao.setArchivedWithChildren(id, true)
            DeleteOutcome.ARCHIVED
        } else {
            dao.delete(id)
            DeleteOutcome.DELETED
        }
    }
}

class RoomRecordRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val clock: Clock,
) : RecordRepository {
    private val dao = db.recordDao()

    override fun observeRecords(filter: RecordFilter): Flow<List<RecordDetails>> =
        dao.observe(
            allAccounts = filter.accountIds.isEmpty(),
            accountIds = filter.accountIds.toList(),
            allCategories = filter.categoryIds.isEmpty(),
            categoryIds = filter.categoryIds.toList(),
            kind = filter.kind,
            from = filter.range?.start,
            to = filter.range?.endExclusive,
            query = filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLike),
            limit = filter.limit,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeMonthSummary(month: YearMonth): Flow<MonthSummary> {
        val range = MonthRange.of(month, clock.zone)
        return dao.observeTotals(range.start, range.endExclusive).map { MonthSummary(it.income, it.expense) }
    }

    override suspend fun get(id: Long): Record? = dao.get(id)?.toDomain()

    override suspend fun save(record: Record): Long = db.withTransaction {
        validate(record)
        val now = clock.millis()
        if (record.id == 0L) {
            dao.insert(record.toEntity(createdAt = now, updatedAt = now))
        } else {
            val existing = requireNotNull(dao.get(record.id)) { "Record ${record.id} not found" }
            dao.update(record.toEntity(createdAt = existing.createdAt, updatedAt = now))
            record.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private suspend fun validate(record: Record) {
        require(record.amount > 0) { "Amount must be more than zero" }
        requireNotNull(db.accountDao().get(record.accountId)) { "Account not found" }
        when (record.kind) {
            RecordKind.TRANSFER -> {
                val to = requireNotNull(record.toAccountId) { "Choose the account to transfer to" }
                require(to != record.accountId) { "Transfer needs two different accounts" }
                requireNotNull(db.accountDao().get(to)) { "Destination account not found" }
                require(record.categoryId == null) { "Transfers don't have a category" }
            }
            RecordKind.INCOME, RecordKind.EXPENSE -> {
                require(record.toAccountId == null) { "Only transfers have a destination account" }
                record.categoryId?.let { id ->
                    val category = requireNotNull(db.categoryDao().get(id)) { "Category not found" }
                    val expected = if (record.kind == RecordKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                    require(category.kind == expected) { "Category type doesn't match the record" }
                }
            }
        }
    }

    /** Escapes LIKE wildcards so a search for "50%" means the literal text. */
    private fun escapeLike(text: String) =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

class RoomRecurringRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
) : RecurringRepository {
    private val dao = db.recurringRuleDao()

    override fun observeRules(): Flow<List<RecurringRule>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): RecurringRule? = dao.get(id)?.toDomain()

    override suspend fun dueOn(date: LocalDate): List<RecurringRule> = dao.dueOn(date.toEpochDay()).map { it.toDomain() }

    override suspend fun save(rule: RecurringRule): Long {
        require(rule.amount > 0) { "Amount must be more than zero" }
        require(rule.interval >= 1) { "Repeat interval must be at least 1" }
        require(rule.endDate == null || !rule.endDate.isBefore(rule.startDate)) { "End date is before the start date" }
        return if (rule.id == 0L) dao.insert(rule.toEntity()) else { dao.update(rule.toEntity()); rule.id }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomBudgetRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val clock: Clock,
) : BudgetRepository {
    private val dao = db.budgetDao()

    override fun observeOverview(month: YearMonth): Flow<BudgetOverview> {
        val range = MonthRange.of(month, clock.zone)
        val recordDao = db.recordDao()
        return combine(
            dao.observeEffective(MonthRange.key(month)),
            recordDao.observeExpenseByCategory(range.start, range.endExclusive),
            recordDao.observeTotals(range.start, range.endExclusive),
            db.categoryDao().observeByKind(CategoryKind.EXPENSE, includeArchived = true),
        ) { budgets, spendRows, totals, categories ->
            buildOverview(month, budgets.map { it.toDomain() }, spendRows.associate { it.categoryId to it.total }, totals.expense, categories)
        }
    }

    override fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>> = observeOverview(month).map { it.all }

    override suspend fun save(budget: Budget): Long {
        require(budget.limit > 0) { "Budget must be more than zero" }
        return db.withTransaction {
            if (budget.scope == BudgetScope.CATEGORY) {
                val id = requireNotNull(budget.categoryId) { "Choose a category" }
                val category = requireNotNull(db.categoryDao().get(id)) { "Category not found" }
                require(category.kind == CategoryKind.EXPENSE) { "Budgets are for expense categories" }
                require(category.parentId == null) { "Budgets are set on main categories" }
            }
            val existing = dao.find(budget.scope, budget.categoryId, MonthRange.key(budget.month))
            if (existing != null) {
                dao.update(budget.toEntity().copy(id = existing.id))
                existing.id
            } else {
                dao.insert(budget.toEntity().copy(id = 0))
            }
        }
    }

    override suspend fun stop(target: BudgetTarget, month: YearMonth): Unit = db.withTransaction {
        val key = MonthRange.key(month)
        val sameMonth = dao.find(target.scope, target.categoryId, key)
        val before = dao.latestBefore(target.scope, target.categoryId, key)
        val activeBefore = before != null && before.limitAmount > 0
        when {
            // Something earlier is still in effect: write a stop marker for this month.
            activeBefore && sameMonth != null -> dao.update(sameMonth.copy(limitAmount = 0))
            activeBefore -> dao.insert(BudgetEntity(scope = target.scope, categoryId = target.categoryId, yearMonth = key, limitAmount = 0))
            // Nothing earlier: just remove this month's row.
            sameMonth != null -> dao.delete(sameMonth.id)
            else -> Unit
        }
    }

    override suspend fun spentIn(target: BudgetTarget, month: YearMonth): Long {
        val overview = observeOverview(month).first()
        overview.all.firstOrNull { it.budget.target == target }?.let { return it.spent }
        // Not budgeted that month: compute directly.
        val range = MonthRange.of(month, clock.zone)
        val recordDao = db.recordDao()
        val total = recordDao.observeTotals(range.start, range.endExclusive).first().expense
        val spend = recordDao.observeExpenseByCategory(range.start, range.endExclusive).first().associate { it.categoryId to it.total }
        return when (target.scope) {
            BudgetScope.OVERALL -> total
            BudgetScope.CATEGORY -> spend[target.categoryId] ?: 0L
            BudgetScope.REST -> total - overview.categories.sumOf { spend[it.budget.categoryId] ?: 0L }
        }
    }

    private fun buildOverview(
        month: YearMonth,
        budgets: List<Budget>,
        spend: Map<Long, Long>,
        totalExpense: Long,
        categories: List<CategoryEntity>,
    ): BudgetOverview {
        val byId = categories.associateBy { it.id }
        val mainActive = categories.filter { it.parentId == null && !it.archived }
        val categoryBudgets = budgets.filter { it.scope == BudgetScope.CATEGORY }
        val budgetedIds = categoryBudgets.mapNotNull { it.categoryId }.toSet()

        val overall = budgets.firstOrNull { it.scope == BudgetScope.OVERALL }?.let {
            BudgetProgress(it, categoryName = null, categoryIconKey = null, spent = totalExpense)
        }
        val categoryProgress = categoryBudgets.map { b ->
            val c = b.categoryId?.let(byId::get)
            BudgetProgress(b, c?.name, c?.iconKey, spent = spend[b.categoryId] ?: 0L)
        }.sortedWith(compareByDescending<BudgetProgress> { it.fraction }.thenBy { it.categoryName })
        val restBudget = budgets.firstOrNull { it.scope == BudgetScope.REST }
        val rest = restBudget?.let {
            BudgetProgress(
                it, categoryName = null, categoryIconKey = null,
                // Everything not counted by a category budget (subcategory/combined spend is already rolled up).
                spent = totalExpense - budgetedIds.sumOf { id -> spend[id] ?: 0L },
                coveredCount = mainActive.count { c -> c.id !in budgetedIds },
            )
        }
        val suggestions = if (restBudget != null) emptyList() else mainActive
            .filter { it.id !in budgetedIds && (spend[it.id] ?: 0L) > 0 }
            .map { BudgetSuggestion(it.id, it.name, it.iconKey, spend.getValue(it.id)) }
            .sortedByDescending { it.spent }
        return BudgetOverview(month, overall, categoryProgress, rest, suggestions)
    }
}
