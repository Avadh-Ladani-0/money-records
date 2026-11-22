package com.moneyrecords.app.data.repository

import androidx.room.withTransaction
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.mapper.toDomain
import com.moneyrecords.app.data.mapper.toEntity
import com.moneyrecords.app.data.mapper.toTree
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetProgress
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
            accountId = filter.accountId,
            categoryId = filter.categoryId,
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

    override fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>> {
        val range = MonthRange.of(month, clock.zone)
        val recordDao = db.recordDao()
        return combine(
            dao.observeForMonth(MonthRange.key(month)),
            recordDao.observeExpenseByCategory(range.start, range.endExclusive),
            recordDao.observeTotals(range.start, range.endExclusive),
            db.categoryDao().observeByKind(CategoryKind.EXPENSE, includeArchived = true),
        ) { budgets, spendRows, totals, categories ->
            val spend = spendRows.associate { it.categoryId to it.total }
            val byId = categories.associateBy { it.id }
            budgets
                .map { entity ->
                    val category = entity.categoryId?.let(byId::get)
                    BudgetProgress(
                        budget = entity.toDomain(),
                        categoryName = category?.name,
                        categoryIconKey = category?.iconKey,
                        spent = if (entity.categoryId == null) totals.expense else spend[entity.categoryId] ?: 0L,
                    )
                }
                .sortedWith(compareBy<BudgetProgress>({ it.budget.categoryId != null }, { it.categoryName }))
        }
    }

    override suspend fun save(budget: Budget): Long {
        require(budget.limit > 0) { "Budget must be more than zero" }
        return db.withTransaction {
            budget.categoryId?.let { id ->
                val category = requireNotNull(db.categoryDao().get(id)) { "Category not found" }
                require(category.kind == CategoryKind.EXPENSE) { "Budgets are for expense categories" }
                require(category.parentId == null) { "Budgets are set on top-level categories" }
            }
            val existing = dao.find(budget.categoryId, MonthRange.key(budget.month))
            if (existing != null) {
                dao.update(budget.toEntity().copy(id = existing.id))
                existing.id
            } else {
                dao.insert(budget.toEntity().copy(id = 0))
            }
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
