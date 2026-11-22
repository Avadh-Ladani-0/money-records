package com.moneyrecords.app.testing

import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.RecordDefaults
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.YearMonth

class FakeAccounts(initial: List<Account> = emptyList()) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        accounts.map { list -> if (includeArchived) list else list.filterNot { it.archived } }
    override fun observeTotalBalance(): Flow<Long> = accounts.map { list -> list.filterNot { it.archived }.sumOf { it.balance } }
    override suspend fun get(id: Long) = accounts.value.firstOrNull { it.id == id }
    override suspend fun save(account: Account): Long {
        val id = if (account.id == 0L) (accounts.value.maxOfOrNull { it.id } ?: 0) + 1 else account.id
        accounts.update { list -> list.filterNot { it.id == id } + account.copy(id = id) }
        return id
    }
    override suspend fun setArchived(id: Long, archived: Boolean) =
        accounts.update { list -> list.map { if (it.id == id) it.copy(archived = archived) else it } }
    override suspend fun deleteOrArchive(id: Long): DeleteOutcome {
        accounts.update { list -> list.filterNot { it.id == id } }
        return DeleteOutcome.DELETED
    }
}

class FakeCategories(initial: Map<CategoryKind, List<Category>>) : CategoryRepository {
    val trees = MutableStateFlow(initial)
    private var nextId = 1000L
    override fun observeTree(kind: CategoryKind, includeArchived: Boolean): Flow<List<Category>> =
        trees.map { it[kind].orEmpty() }
    override suspend fun get(id: Long) = trees.value.values.flatten().flatMap { listOf(it) + it.children }.firstOrNull { it.id == id }
    override suspend fun save(category: Category): Long {
        require(category.name.isNotBlank()) { "Category name is required" }
        val saved = category.copy(id = if (category.id == 0L) nextId++ else category.id)
        trees.update { map ->
            // Remove any previous version first, so saving an existing category replaces it.
            val tree = map[saved.kind].orEmpty()
                .filterNot { it.id == saved.id }
                .map { p -> p.copy(children = p.children.filterNot { it.id == saved.id }) }
            val updated = if (saved.parentId == null) tree + saved
            else tree.map { p -> if (p.id == saved.parentId) p.copy(children = p.children + saved) else p }
            map + (saved.kind to updated)
        }
        return saved.id
    }
    override suspend fun setArchived(id: Long, archived: Boolean) = Unit
    override suspend fun deleteOrArchive(id: Long) = DeleteOutcome.DELETED
    override suspend fun combine(firstId: Long, secondId: Long, name: String): Long {
        val first = requireNotNull(get(firstId)); val second = requireNotNull(get(secondId))
        return save(Category(name = name, kind = first.kind, parentId = first.id,
            iconKey = com.moneyrecords.app.domain.repository.ComboIcon.key(first.iconKey, second.iconKey)))
    }
}

class FakeRecords(initial: List<Record> = emptyList()) : RecordRepository {
    val records = MutableStateFlow(initial)
    var lastFilter: RecordFilter? = null
    /** Names used to build RecordDetails; tests can fill these in. */
    var details: (Record) -> RecordDetails = { RecordDetails(it, "Account ${it.accountId}", null, "Category ${it.categoryId}", null) }

    override fun observeRecords(filter: RecordFilter): Flow<List<RecordDetails>> {
        lastFilter = filter
        return records.map { list ->
            list.filter { r -> filter.range == null || r.occurredAt.toEpochMilli() in filter.range.start until filter.range.endExclusive }
                .sortedByDescending { it.occurredAt }
                .map(details)
        }
    }
    override fun observeMonthSummary(month: YearMonth): Flow<MonthSummary> = records.map { list ->
        MonthSummary(list.filter { it.kind == RecordKind.INCOME }.sumOf { it.amount }, list.filter { it.kind == RecordKind.EXPENSE }.sumOf { it.amount })
    }
    override suspend fun get(id: Long) = records.value.firstOrNull { it.id == id }
    var zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")
    override suspend fun countOn(date: java.time.LocalDate) = records.value.count { it.occurredAt.atZone(zone).toLocalDate() == date }
    override suspend fun save(record: Record): Long {
        require(record.amount > 0) { "Amount must be more than zero" }
        val id = if (record.id == 0L) (records.value.maxOfOrNull { it.id } ?: 0) + 1 else record.id
        records.update { list -> list.filterNot { it.id == id } + record.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = records.update { list -> list.filterNot { it.id == id } }
}

class FakePrefs : UserPrefsRepository {
    val defaults = mutableMapOf<RecordKind, RecordDefaults>()
    val calculator = MutableStateFlow(CalculatorPrefs())
    var lastPrompt: YearMonth? = null
    val notificationPrefs = MutableStateFlow(com.moneyrecords.app.domain.repository.NotificationPrefs())
    val sent = mutableSetOf<String>()
    override fun observeNotifications() = notificationPrefs
    override suspend fun notifications() = notificationPrefs.value
    override suspend fun updateNotifications(change: (com.moneyrecords.app.domain.repository.NotificationPrefs) -> com.moneyrecords.app.domain.repository.NotificationPrefs) =
        notificationPrefs.update(change)
    override suspend fun markSent(key: String) = sent.add(key)
    val appLock = MutableStateFlow(com.moneyrecords.app.domain.lock.AppLockPrefs())
    override fun observeAppLock() = appLock
    override suspend fun updateAppLock(change: (com.moneyrecords.app.domain.lock.AppLockPrefs) -> com.moneyrecords.app.domain.lock.AppLockPrefs) = appLock.update(change)
    override suspend fun lastBudgetPrompt() = lastPrompt
    override suspend fun setLastBudgetPrompt(month: YearMonth) { lastPrompt = month }
    override suspend fun recordDefaults(kind: RecordKind) = defaults[kind] ?: RecordDefaults()
    override suspend fun rememberRecordDefaults(kind: RecordKind, defaults: RecordDefaults) { this.defaults[kind] = defaults }
    val theme = MutableStateFlow("NOTHING")
    override fun observeTheme(): Flow<String> = theme
    override suspend fun setTheme(key: String) { theme.value = key }
    val backup = MutableStateFlow(com.moneyrecords.app.domain.repository.BackupPrefs())
    override fun observeBackup() = backup
    override suspend fun updateBackup(change: (com.moneyrecords.app.domain.repository.BackupPrefs) -> com.moneyrecords.app.domain.repository.BackupPrefs) = backup.update(change)
    override fun observeCalculator(): Flow<CalculatorPrefs> = calculator
    override suspend fun setCalculatorEnabled(enabled: Boolean) = calculator.update { it.copy(enabled = enabled) }
    override suspend fun setCalculatorPosition(x: Float, y: Float) = calculator.update { it.copy(x = x, y = y) }
}

class FakeBudgets : BudgetRepository {
    /** Overviews by month; months not set return an empty overview. */
    val overviews = MutableStateFlow<Map<YearMonth, BudgetOverview>>(emptyMap())
    val saved = mutableListOf<Budget>()
    val stopped = mutableListOf<Pair<BudgetTarget, YearMonth>>()
    var spent: (BudgetTarget, YearMonth) -> Long = { _, _ -> 0L }
    override fun observeOverview(month: YearMonth): Flow<BudgetOverview> =
        overviews.map { it[month] ?: BudgetOverview(month, null, emptyList(), null, emptyList()) }
    override fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>> = observeOverview(month).map { it.all }
    override suspend fun save(budget: Budget): Long { require(budget.limit > 0); saved += budget; return saved.size.toLong() }
    override suspend fun stop(target: BudgetTarget, month: YearMonth) { stopped += target to month }
    override suspend fun spentIn(target: BudgetTarget, month: YearMonth) = spent(target, month)
    val offsets = mutableMapOf<Pair<BudgetTarget, YearMonth>, Set<Long>>()
    var candidates: List<RecordDetails> = emptyList()
    override suspend fun offsetCandidates(target: BudgetTarget, month: YearMonth) = candidates
    override suspend fun selectedOffsets(target: BudgetTarget, month: YearMonth) = offsets[target to month].orEmpty()
    override suspend fun setOffsets(target: BudgetTarget, month: YearMonth, recordIds: Set<Long>) { offsets[target to month] = recordIds }
}

class FakeRecurring(private val records: FakeRecords) : com.moneyrecords.app.domain.repository.RecurringRepository {
    val rules = MutableStateFlow<List<com.moneyrecords.app.domain.model.RecurringRule>>(emptyList())
    private fun details(r: com.moneyrecords.app.domain.model.RecurringRule) =
        com.moneyrecords.app.domain.model.RecurringRuleDetails(r, "Account ${r.accountId}", null, "Category ${r.categoryId}", null)
    override fun observeRules() = rules.map { list -> list.map(::details) }
    override suspend fun get(id: Long) = rules.value.firstOrNull { it.id == id }
    override suspend fun getDetails(id: Long) = get(id)?.let(::details)
    override fun observeDue(today: java.time.LocalDate) = rules.map { list ->
        list.flatMap { r -> com.moneyrecords.app.domain.recurring.Recurrence.dueDates(r, today).map { com.moneyrecords.app.domain.model.DueOccurrence(details(r), it) } }
    }
    override suspend fun save(rule: com.moneyrecords.app.domain.model.RecurringRule): Long {
        val id = if (rule.id == 0L) (rules.value.maxOfOrNull { it.id } ?: 0) + 1 else rule.id
        rules.update { list -> list.filterNot { it.id == id } + rule.copy(id = id) }
        return id
    }
    override suspend fun confirm(ruleId: Long, override: Record?): Long {
        val r = requireNotNull(get(ruleId))
        val id = records.save((override ?: Record(kind = r.kind, amount = r.amount, accountId = r.accountId, categoryId = r.categoryId,
            occurredAt = r.nextRunDate.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant())).copy(id = 0, recurringRuleId = r.id))
        skip(ruleId)
        return id
    }
    override suspend fun skip(ruleId: Long) {
        val r = requireNotNull(get(ruleId))
        save(r.copy(nextRunDate = com.moneyrecords.app.domain.recurring.Recurrence.next(r, r.nextRunDate)))
    }
    override suspend fun setActive(id: Long, active: Boolean) { get(id)?.let { save(it.copy(active = active)) } }
    override suspend fun delete(id: Long) = rules.update { list -> list.filterNot { it.id == id } }
}

class FakeAlerts : com.moneyrecords.app.notify.BudgetAlerts {
    val checked = mutableListOf<YearMonth>()
    override suspend fun checkAfterRecord(month: YearMonth) { checked += month }
}

object TestData {
    val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, iconKey = "cash")
    val bank = Account(id = 2, name = "Bank", type = AccountType.BANK, iconKey = "bank")
    val food = Category(id = 10, name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food",
        children = listOf(Category(id = 11, name = "Snacks", kind = CategoryKind.EXPENSE, parentId = 10, iconKey = "food")))
    val fuel = Category(id = 12, name = "Fuel", kind = CategoryKind.EXPENSE, iconKey = "fuel")
    val salary = Category(id = 20, name = "Salary", kind = CategoryKind.INCOME, iconKey = "salary")
    val trees = mapOf(CategoryKind.EXPENSE to listOf(food, fuel), CategoryKind.INCOME to listOf(salary))
}
