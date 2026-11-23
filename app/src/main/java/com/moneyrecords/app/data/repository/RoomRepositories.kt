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
import com.moneyrecords.app.data.db.TotalsRow
import com.moneyrecords.app.data.db.CategorySpend
import com.moneyrecords.app.data.db.BudgetOffsetEntity
import com.moneyrecords.app.data.db.AccountEntity
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
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.model.DueOccurrence
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

    override suspend fun countOn(date: LocalDate): Int {
        val start = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return dao.countBetween(start, end)
    }

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

    /** Deleting a split expense also removes its to-collect parts and their repayments. */
    override suspend fun delete(id: Long) = db.withTransaction {
        dao.deleteGrandchildren(id)
        dao.deleteChildren(id)
        dao.delete(id)
    }

    private suspend fun validate(record: Record) {
        // A split bill may leave your share at ₹0 (you paid entirely for someone else).
        require(record.amount > 0 || (record.isSplit && record.amount == 0L)) { "Amount must be more than zero" }
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
    private val records: RecordRepository,
    private val clock: Clock,
) : RecurringRepository {
    private val dao = db.recurringRuleDao()

    override fun observeRules(): Flow<List<RecurringRuleDetails>> =
        dao.observeAllWithNames().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): RecurringRule? = dao.get(id)?.toDomain()

    override suspend fun getDetails(id: Long): RecurringRuleDetails? =
        dao.observeAllWithNames().first().firstOrNull { it.rule.id == id }?.toDomain()

    override fun observeDue(today: LocalDate): Flow<List<DueOccurrence>> = observeRules().map { rules ->
        rules.flatMap { d -> Recurrence.dueDates(d.rule, today).map { DueOccurrence(d, it) } }.sortedBy { it.date }
    }

    override suspend fun save(rule: RecurringRule): Long {
        require(rule.amount > 0) { "Amount must be more than zero" }
        require(rule.interval >= 1) { "Repeat interval must be at least 1" }
        require(rule.endDate == null || !rule.endDate.isBefore(rule.startDate)) { "End date is before the start date" }
        if (rule.kind == RecordKind.TRANSFER) {
            require(rule.toAccountId != null && rule.toAccountId != rule.accountId) { "Transfer needs two different accounts" }
        }
        return if (rule.id == 0L) dao.insert(rule.toEntity()) else { dao.update(rule.toEntity()); rule.id }
    }

    override suspend fun confirm(ruleId: Long, override: Record?): Long = db.withTransaction {
        val rule = requireNotNull(dao.get(ruleId)?.toDomain()) { "Recurring item not found" }
        require(rule.active && Recurrence.withinEnd(rule, rule.nextRunDate)) { "Nothing is due for this item" }
        val planned = Record(
            kind = rule.kind,
            amount = rule.amount,
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            categoryId = rule.categoryId,
            occurredAt = rule.nextRunDate.atTime(DUE_HOUR, 0).atZone(clock.zone).toInstant(),
            note = rule.note,
        )
        val recordId = records.save((override ?: planned).copy(id = 0, recurringRuleId = rule.id))
        advance(rule)
        recordId
    }

    override suspend fun skip(ruleId: Long) = db.withTransaction {
        val rule = requireNotNull(dao.get(ruleId)?.toDomain()) { "Recurring item not found" }
        advance(rule)
    }

    /** Moves the rule past its current occurrence; ends it when the next date is past the end date. */
    private suspend fun advance(rule: RecurringRule) {
        val next = Recurrence.next(rule, rule.nextRunDate)
        dao.update(rule.copy(nextRunDate = next, active = rule.active && Recurrence.withinEnd(rule, next)).toEntity())
    }

    override suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)

    override suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        /** Due occurrences are recorded at 9:00 on their date. */
        const val DUE_HOUR = 9
    }
}

class RoomBudgetRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val clock: Clock,
) : BudgetRepository {
    private val dao = db.budgetDao()
    private val offsets = db.budgetOffsetDao()

    /** Everything needed to compute a month's budgets, gathered from several live queries. */
    private data class MonthData(
        val budgets: List<Budget>,
        val categorySpend: Map<Long, Long>,
        val totalExpense: Long,
        val categories: List<CategoryEntity>,
        val accountOutflow: Map<Long, Long>,
        val accounts: List<AccountEntity>,
        val offsets: Map<BudgetTarget, Long>,
    )

    private fun observeMonthData(month: YearMonth): Flow<MonthData> {
        val range = MonthRange.of(month, clock.zone)
        val recordDao = db.recordDao()
        val categorySide = combine(
            dao.observeEffective(MonthRange.key(month)),
            recordDao.observeExpenseByCategory(range.start, range.endExclusive),
            recordDao.observeTotals(range.start, range.endExclusive),
            db.categoryDao().observeByKind(CategoryKind.EXPENSE, includeArchived = true),
        ) { budgets, spendRows, totals, categories -> listOf(budgets, spendRows, totals, categories) }
        val accountSide = combine(
            recordDao.observeOutflowByAccount(range.start, range.endExclusive),
            db.accountDao().observeWithBalance(includeArchived = true),
            offsets.observeInRange(range.start, range.endExclusive),
        ) { outflow, accounts, offsetRows -> Triple(outflow, accounts, offsetRows) }
        return combine(categorySide, accountSide) { c, (outflow, accounts, offsetRows) ->
            @Suppress("UNCHECKED_CAST")
            MonthData(
                budgets = (c[0] as List<BudgetEntity>).map { it.toDomain() },
                categorySpend = (c[1] as List<CategorySpend>).associate { it.categoryId to it.total },
                totalExpense = (c[2] as TotalsRow).expense,
                categories = c[3] as List<CategoryEntity>,
                accountOutflow = outflow.associate { it.accountId to it.total },
                accounts = accounts.map { it.account },
                offsets = offsetRows
                    .groupBy { BudgetTarget(it.scope, it.categoryId, it.accountId) }
                    .mapValues { (_, rows) -> rows.sumOf { it.amount } },
            )
        }
    }

    override fun observeOverview(month: YearMonth): Flow<BudgetOverview> =
        observeMonthData(month).map { buildOverview(month, it) }

    override fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>> = observeOverview(month).map { it.all }

    override suspend fun save(budget: Budget): Long {
        require(budget.limit > 0) { "Budget must be more than zero" }
        return db.withTransaction {
            when (budget.scope) {
                BudgetScope.CATEGORY -> {
                    val id = requireNotNull(budget.categoryId) { "Choose a category" }
                    val category = requireNotNull(db.categoryDao().get(id)) { "Category not found" }
                    require(category.kind == CategoryKind.EXPENSE) { "Budgets are for expense categories" }
                    require(category.parentId == null) { "Budgets are set on main categories" }
                }
                BudgetScope.ACCOUNT -> {
                    val id = requireNotNull(budget.accountId) { "Choose an account" }
                    val account = requireNotNull(db.accountDao().get(id)) { "Account not found" }
                    require(!account.archived) { "Archived accounts can't get a budget" }
                }
                else -> Unit
            }
            val existing = dao.find(budget.scope, budget.categoryId, budget.accountId, MonthRange.key(budget.month))
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
        val sameMonth = dao.find(target.scope, target.categoryId, target.accountId, key)
        val before = dao.latestBefore(target.scope, target.categoryId, target.accountId, key)
        val activeBefore = before != null && before.limitAmount > 0
        when {
            // Something earlier is still in effect: write a stop marker for this month.
            activeBefore && sameMonth != null -> dao.update(sameMonth.copy(limitAmount = 0))
            activeBefore -> dao.insert(
                BudgetEntity(scope = target.scope, categoryId = target.categoryId, accountId = target.accountId, yearMonth = key, limitAmount = 0),
            )
            // Nothing earlier: just remove this month's row.
            sameMonth != null -> dao.delete(sameMonth.id)
            else -> Unit
        }
    }

    override suspend fun spentIn(target: BudgetTarget, month: YearMonth): Long {
        val data = observeMonthData(month).first()
        val overview = buildOverview(month, data)
        overview.all.firstOrNull { it.budget.target == target }?.let { return it.spent }
        // Not budgeted that month: compute what it would have been (no offsets).
        val budgetedCategories = overview.categories.mapNotNull { it.budget.categoryId }.toSet()
        val budgetedAccounts = overview.accounts.mapNotNull { it.budget.accountId }.toSet()
        return when (target.scope) {
            BudgetScope.OVERALL -> data.totalExpense
            BudgetScope.CATEGORY -> data.categorySpend[target.categoryId] ?: 0L
            BudgetScope.REST -> data.totalExpense - budgetedCategories.sumOf { data.categorySpend[it] ?: 0L }
            BudgetScope.ACCOUNT -> data.accountOutflow[target.accountId] ?: 0L
            BudgetScope.OTHER_ACCOUNTS -> data.accountOutflow.filterKeys { it !in budgetedAccounts }.values.sum()
        }
    }

    override suspend fun offsetCandidates(target: BudgetTarget, month: YearMonth): List<RecordDetails> {
        val range = MonthRange.of(month, clock.zone)
        return db.recordDao().offsetCandidates(target.accountId, range.start, range.endExclusive).map { it.toDomain() }
    }

    override suspend fun selectedOffsets(target: BudgetTarget, month: YearMonth): Set<Long> {
        val range = MonthRange.of(month, clock.zone)
        return offsets.selectedIds(target.scope, target.categoryId, target.accountId, range.start, range.endExclusive).toSet()
    }

    override suspend fun setOffsets(target: BudgetTarget, month: YearMonth, recordIds: Set<Long>) = db.withTransaction {
        val range = MonthRange.of(month, clock.zone)
        val allowed = offsetCandidates(target, month).map { it.record.id }.toSet()
        require(recordIds.all { it in allowed }) { "Only income or transfers in this month can be counted back" }
        offsets.clear(target.scope, target.categoryId, target.accountId, range.start, range.endExclusive)
        offsets.insertAll(
            recordIds.map { BudgetOffsetEntity(scope = target.scope, categoryId = target.categoryId, accountId = target.accountId, recordId = it) },
        )
    }

    private fun buildOverview(month: YearMonth, d: MonthData): BudgetOverview {
        val categoriesById = d.categories.associateBy { it.id }
        val accountsById = d.accounts.associateBy { it.id }
        val mainActive = d.categories.filter { it.parentId == null && !it.archived }
        fun progress(b: Budget, gross: Long, coveredCount: Int = 0): BudgetProgress {
            val offset = d.offsets[b.target] ?: 0L
            val category = b.categoryId?.let(categoriesById::get)
            val account = b.accountId?.let(accountsById::get)
            return BudgetProgress(
                budget = b,
                categoryName = category?.name,
                categoryIconKey = category?.iconKey,
                spent = (gross - offset).coerceAtLeast(0),
                coveredCount = coveredCount,
                accountName = account?.name,
                accountIconKey = account?.iconKey,
                offset = offset,
            )
        }
        fun List<BudgetProgress>.mostUsedFirst() =
            sortedWith(compareByDescending<BudgetProgress> { it.fraction }.thenBy { it.categoryName ?: it.accountName })

        // Category side.
        val categoryBudgets = d.budgets.filter { it.scope == BudgetScope.CATEGORY }
        val budgetedCategories = categoryBudgets.mapNotNull { it.categoryId }.toSet()
        val overall = d.budgets.firstOrNull { it.scope == BudgetScope.OVERALL }?.let { progress(it, d.totalExpense) }
        val categories = categoryBudgets.map { progress(it, d.categorySpend[it.categoryId] ?: 0L) }.mostUsedFirst()
        val restBudget = d.budgets.firstOrNull { it.scope == BudgetScope.REST }
        val rest = restBudget?.let {
            // Everything not counted by a category budget (subcategory/combined spend is already rolled up).
            progress(
                it,
                gross = d.totalExpense - budgetedCategories.sumOf { id -> d.categorySpend[id] ?: 0L },
                coveredCount = mainActive.count { c -> c.id !in budgetedCategories },
            )
        }
        val suggestions = if (restBudget != null) emptyList() else mainActive
            .filter { it.id !in budgetedCategories && (d.categorySpend[it.id] ?: 0L) > 0 }
            .map { BudgetSuggestion(it.id, it.name, it.iconKey, d.categorySpend.getValue(it.id)) }
            .sortedByDescending { it.spent }

        // Account side: expenses + transfers out.
        val accountBudgets = d.budgets.filter { it.scope == BudgetScope.ACCOUNT }
        val budgetedAccounts = accountBudgets.mapNotNull { it.accountId }.toSet()
        val accounts = accountBudgets.map { progress(it, d.accountOutflow[it.accountId] ?: 0L) }.mostUsedFirst()
        val otherAccounts = d.budgets.firstOrNull { it.scope == BudgetScope.OTHER_ACCOUNTS }?.let {
            progress(
                it,
                gross = d.accountOutflow.filterKeys { id -> id !in budgetedAccounts }.values.sum(),
                coveredCount = d.accounts.count { a -> !a.archived && a.id !in budgetedAccounts },
            )
        }
        return BudgetOverview(month, overall, categories, rest, suggestions, accounts, otherAccounts)
    }
}
