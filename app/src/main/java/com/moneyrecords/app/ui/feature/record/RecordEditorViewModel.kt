package com.moneyrecords.app.ui.feature.record

import com.moneyrecords.app.domain.repository.SplitRepository
import com.moneyrecords.app.domain.split.Split
import com.moneyrecords.app.domain.split.SplitPart
import com.moneyrecords.app.domain.split.SplitSpec
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.calc.Expression
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.notify.BudgetAlerts
import java.time.YearMonth
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.RecordDefaults
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.ui.components.KeypadKey
import com.moneyrecords.app.ui.components.applyKey
import com.moneyrecords.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

enum class EditorMode { NEW, EDIT, DUPLICATE, RECURRING_DUE }

/**
 * How a new record should come back (null = it doesn't).
 * - [once]: comes back one time, on [nextDate].
 * - otherwise repeats every [interval] [frequency]; [nextDate] overrides the natural next date
 *   (e.g. "on the 5th from next month"), and later dates follow from it.
 */
data class RepeatSpec(
    val frequency: Frequency,
    val interval: Int = 1,
    val endDate: LocalDate? = null,
    val nextDate: LocalDate? = null,
    val once: Boolean = false,
) {
    /** The first date it comes back, for a record dated [recordDate]. */
    fun nextFrom(recordDate: LocalDate): LocalDate = nextDate ?: if (once) {
        recordDate.plusMonths(1)
    } else {
        Recurrence.next(recordDate, frequency, interval, recordDate)
    }

    /** The rule this creates for a record dated [recordDate]. */
    fun toRule(template: RecurringRule, recordDate: LocalDate): RecurringRule {
        val next = nextFrom(recordDate)
        return template.copy(
            frequency = frequency,
            interval = interval,
            // A chosen next date becomes the anchor (e.g. the 5th of each month); otherwise keep the
            // record's own date so month-end handling stays right (31 Jan → 28 Feb → 31 Mar).
            startDate = if (once || nextDate != null) next else recordDate,
            endDate = if (once) next else endDate,
            nextRunDate = next,
        )
    }
}

data class RecordEditorState(
    val mode: EditorMode = EditorMode.NEW,
    val loading: Boolean = true,
    val kind: RecordKind = RecordKind.EXPENSE,
    /** Raw keypad input, may contain + − × ÷. */
    val amountText: String = "",
    /** Accounts offered for selection (active ones, plus the record's own if archived). */
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    /** Category tree for the current kind. */
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    /** Most recently used categories for this kind, newest first (for the picker's "Recent" row). */
    val recentCategoryIds: List<Long> = emptyList(),
    val date: LocalDate = LocalDate.MIN,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val note: String = "",
    /** New records only: makes this a recurring record. */
    val repeat: RepeatSpec? = null,
    /** Editing a record created by a repeat: that repeat (kept up to date). */
    val linkedRule: RecurringRuleDetails? = null,
    /** Expenses only: this bill was paid for a group. Amount on screen = the whole bill. */
    val split: SplitDraft? = null,
    /** Something was already paid back, so the bill amount and split can't change. */
    val splitLocked: Boolean = false,
    val people: List<String> = emptyList(),
    /** The caught payment being recorded, if any. */
    val fromDetected: com.moneyrecords.app.domain.detect.Detected? = null,
    /** Accounts its app can pay from (UPI Lite first) — shown as one-tap chips. */
    val quickAccounts: List<Long> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
) {
    /** The split as it would be saved, for the current amount. */
    val splitSpec: SplitSpec? get() = split?.let { d -> amountPaise?.let { d.toSpec(it) } }

    val amountPaise: Long? get() = Expression.evaluateToPaise(amountText)

    /** "= ₹1,550" when the input is a calculation with a valid result. */
    val preview: String?
        get() = if (Expression.hasOperator(amountText)) amountPaise?.let { "= " + Money.format(it) } else null

    val needsAccount: Boolean get() = !loading && accounts.isEmpty()

    /** The picked category, whether top-level or a subcategory. */
    val selectedCategory: Category?
        get() = categories.flatMap { listOf(it) + it.children }.firstOrNull { it.id == categoryId }

    val canSave: Boolean
        get() = !saving && (amountPaise ?: 0) > 0 && accountId != null && when (kind) {
            RecordKind.TRANSFER -> toAccountId != null && toAccountId != accountId
            else -> categoryId != null
        }
}

sealed interface RecordEditorEvent {
    data object Saved : RecordEditorEvent
    /** Closed without saving (a caught payment put off or ignored). */
    data object Closed : RecordEditorEvent
    /** [split] is set when a split bill was deleted, so Undo can bring the whole split back. */
    data class Deleted(val record: Record, val split: SplitSpec? = null, val canUndo: Boolean = true) : RecordEditorEvent
    /** Deleting was refused; [reason] says why (shown in a dialog). */
    data class CantDelete(val reason: String) : RecordEditorEvent
}

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val records: RecordRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val prefs: UserPrefsRepository,
    private val recurring: RecurringRepository,
    private val alerts: BudgetAlerts,
    private val splits: SplitRepository,
    private val detection: com.moneyrecords.app.domain.detect.DetectionRepository,
    private val detectedNotifier: com.moneyrecords.app.domain.detect.DetectedPrompts,
    private val clock: Clock,
) : ViewModel() {
    private val recordId: Long = handle.get<Long>(RouteArgs.RECORD_ID) ?: 0L
    private val duplicateOf: Long = handle.get<Long>(RouteArgs.DUPLICATE_OF) ?: 0L
    private val fromRuleId: Long = handle.get<Long>(RouteArgs.FROM_RULE_ID) ?: 0L
    /** A caught payment this record comes from (pre-fills it; marked added on save). */
    private val detectedId: Long = handle.get<Long>(RouteArgs.DETECTED_ID) ?: 0L
    /** Type to start a new record with (from the Quick add widget); null = expense. */
    private val startKind: RecordKind? = handle.get<String>(RouteArgs.KIND)?.let { k -> RecordKind.entries.firstOrNull { it.name == k } }
    private val mode = when {
        recordId != 0L -> EditorMode.EDIT
        duplicateOf != 0L -> EditorMode.DUPLICATE
        fromRuleId != 0L -> EditorMode.RECURRING_DUE
        else -> EditorMode.NEW
    }

    private val now: LocalDateTime get() = LocalDateTime.now(clock)
    private val _state = MutableStateFlow(RecordEditorState(mode = mode, date = now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0)))
    val state: StateFlow<RecordEditorState> = _state.asStateFlow()
    private val _events = Channel<RecordEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var allAccounts: List<Account> = emptyList()
    private var trees: Map<CategoryKind, List<Category>> = emptyMap()
    private var initialised = false
    /** Editing a bill that was split when opened. */
    private var wasSplit = false

    init {
        viewModelScope.launch {
            combine(
                accounts.observeAccounts(includeArchived = true),
                categories.observeTree(CategoryKind.EXPENSE, includeArchived = true),
                categories.observeTree(CategoryKind.INCOME, includeArchived = true),
            ) { a, e, i -> Triple(a, e, i) }.collect { (a, e, i) ->
                allAccounts = a
                trees = mapOf(CategoryKind.EXPENSE to e, CategoryKind.INCOME to i)
                if (!initialised) {
                    initialised = true
                    loadInitial()
                }
                refreshOptions()
            }
        }
    }

    private suspend fun loadInitial() {
        if (mode == EditorMode.RECURRING_DUE) {
            val rule = recurring.get(fromRuleId)
            if (rule == null) {
                _state.update { it.copy(error = "Recurring item not found") }
            } else {
                _state.update {
                    it.copy(
                        kind = rule.kind,
                        amountText = Expression.toPlainText(BigDecimal(rule.amount).movePointLeft(2)),
                        accountId = rule.accountId,
                        toAccountId = rule.toAccountId,
                        categoryId = rule.categoryId,
                        note = rule.note,
                        date = rule.nextRunDate,
                        time = LocalTime.of(9, 0),
                    )
                }
            }
            loadRecents(_state.value.kind)
            _state.update { it.copy(loading = false) }
            return
        }
        val source = when (mode) {
            EditorMode.EDIT -> records.get(recordId)
            EditorMode.DUPLICATE -> records.get(duplicateOf)
            EditorMode.NEW -> null
        }
        if (source != null) {
            if (mode == EditorMode.EDIT) source.recurringRuleId?.let(::watchLinkedRule)
            val at = LocalDateTime.ofInstant(source.occurredAt, clock.zone)
            // A split bill opens with the whole bill as the amount and its split ready to edit.
            val spec = if (source.isSplit) splits.splitOf(source.id) else null
            if (mode == EditorMode.EDIT) wasSplit = spec != null
            val locked = mode == EditorMode.EDIT && spec != null && splits.hasRepayments(source.id)
            _state.update {
                it.copy(
                    kind = source.kind,
                    split = spec?.let(SplitDraft::from),
                    splitLocked = locked,
                    amountText = Expression.toPlainText(BigDecimal(source.splitTotal ?: source.amount).movePointLeft(2)),
                    accountId = source.accountId,
                    toAccountId = source.toAccountId,
                    categoryId = source.categoryId,
                    note = source.note,
                    date = if (mode == EditorMode.EDIT) at.toLocalDate() else it.date,
                    time = if (mode == EditorMode.EDIT) at.toLocalTime() else it.time,
                )
            }
        } else {
            if (mode != EditorMode.NEW) _state.update { it.copy(error = "Record not found") }
            applyDefaults(startKind ?: RecordKind.EXPENSE)
            if (detectedId > 0) prefillFromDetected()
        }
        loadRecents(_state.value.kind)
        _state.update { it.copy(loading = false) }
    }

    /** Fills the editor from a caught payment: type, amount, account(s), payee as note, time, category guess. */
    private suspend fun prefillFromDetected() {
        val d = detection.refresh(detectedId) ?: return
        val kind = when {
            d.isTransfer -> RecordKind.TRANSFER
            d.direction == com.moneyrecords.app.domain.detect.Direction.OUT -> RecordKind.EXPENSE
            else -> RecordKind.INCOME
        }
        if (kind != _state.value.kind) applyDefaults(kind)
        val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(d.occurredAt), clock.zone)
        val guess = detection.categoryFor(d.counterparty, d.direction)
        val account = detection.guessAccount(d)
        val quick = if (d.source == com.moneyrecords.app.domain.detect.DetectSource.SMS) emptyList() else detection.accountsFor(d.origin)
        _state.update {
            it.copy(
                fromDetected = d,
                quickAccounts = quick,
                amountText = if (d.amount > 0) Expression.toPlainText(BigDecimal(d.amount).movePointLeft(2)) else it.amountText,
                accountId = account ?: it.accountId,
                toAccountId = if (kind == RecordKind.TRANSFER) d.toAccountId else it.toAccountId,
                categoryId = guess ?: it.categoryId,
                note = d.counterparty.orEmpty(),
                date = at.toLocalDate(),
                time = at.toLocalTime().withSecond(0).withNano(0),
            )
        }
    }

    /** Set once the caught payment is recorded or dismissed — otherwise leaving brings its prompt back. */
    private var detectedHandled = false

    init {
        if (detectedId > 0) detectedNotifier.editing(detectedId, true)
    }

    override fun onCleared() {
        if (detectedId > 0) detectedNotifier.editing(detectedId, false)
        // Closed without recording (and not "No payment made"): keep reminding until it's recorded.
        if (detectedId > 0 && !detectedHandled) detectedNotifier.remindIfOpen(detectedId)
        super.onCleared()
    }

    /** "Later": keep it in To record (the ongoing reminder stays). */
    fun detectedLater() {
        if (detectedId == 0L) return
        detectedHandled = true // the ongoing "payments to record" keeps it in view
        viewModelScope.launch {
            detection.setStatus(detectedId, com.moneyrecords.app.domain.detect.DetectStatus.LATER)
            detectedNotifier.refreshSummary()
            _events.send(RecordEditorEvent.Closed)
        }
    }

    /** "Ignore" / "No payment made": not a record. */
    fun detectedIgnore() {
        if (detectedId == 0L) return
        viewModelScope.launch {
            detectedHandled = true
            detection.setStatus(detectedId, com.moneyrecords.app.domain.detect.DetectStatus.IGNORED)
            detectedNotifier.cancel(detectedId)
            detectedNotifier.refreshSummary()
            _events.send(RecordEditorEvent.Closed)
        }
    }

    /** Shows the repeat this record came from, refreshing after it's edited elsewhere. */
    private fun watchLinkedRule(ruleId: Long) {
        viewModelScope.launch {
            recurring.observeRules().collect { rules ->
                _state.update { it.copy(linkedRule = rules.firstOrNull { r -> r.rule.id == ruleId }) }
            }
        }
    }

    private suspend fun loadRecents(kind: RecordKind) {
        if (kind == RecordKind.TRANSFER) return _state.update { it.copy(recentCategoryIds = emptyList()) }
        val recent = records.observeRecords(RecordFilter(kind = kind, limit = 60)).first()
            .filter { it.record.kind == kind }
            .mapNotNull { it.record.categoryId }
            .distinct()
            .take(6)
        _state.update { it.copy(recentCategoryIds = recent) }
    }

    /** Pre-selects the last-used account/category for [kind], falling back to the first active account. */
    private suspend fun applyDefaults(kind: RecordKind) {
        val remembered = prefs.recordDefaults(kind)
        val active = allAccounts.filterNot { it.archived }
        val account = active.firstOrNull { it.id == remembered.accountId } ?: active.firstOrNull()
        val toAccount = active.firstOrNull { it.id == remembered.toAccountId && it.id != account?.id }
            ?: active.firstOrNull { it.id != account?.id }
        val tree = kind.categoryKind()?.let { trees[it] }.orEmpty()
        val category = remembered.categoryId?.takeIf { id -> tree.flatten().any { it.id == id && !it.archived } }
        _state.update {
            it.copy(
                kind = kind,
                accountId = account?.id,
                toAccountId = if (kind == RecordKind.TRANSFER) toAccount?.id else null,
                categoryId = if (kind == RecordKind.TRANSFER) null else category,
            )
        }
    }

    /** Recomputes the account and category options shown, keeping selected archived items visible. */
    private fun refreshOptions() {
        _state.update { s ->
            val keep = setOfNotNull(s.accountId, s.toAccountId)
            val tree = s.kind.categoryKind()?.let { trees[it] }.orEmpty()
            s.copy(
                accounts = allAccounts.filter { !it.archived || it.id in keep },
                categories = tree
                    .filter { !it.archived || it.id == s.categoryId || it.children.any { c -> c.id == s.categoryId } }
                    .map { parent -> parent.copy(children = parent.children.filter { !it.archived || it.id == s.categoryId }) },
            )
        }
    }

    fun onKind(kind: RecordKind) {
        if (kind == _state.value.kind) return
        viewModelScope.launch {
            applyDefaults(kind)
            refreshOptions()
            loadRecents(kind)
        }
    }

    /** Whether [key] would change the amount (refused keys get a "reject" buzz). */
    fun accepts(key: KeypadKey): Boolean {
        val text = _state.value.amountText
        return key == KeypadKey.Clear || applyKey(text, key, evaluate = { t -> Expression.evaluate(t)?.let(Expression::toPlainText) }) != text
    }

    fun onKey(key: KeypadKey) = _state.update {
        it.copy(
            amountText = applyKey(it.amountText, key, evaluate = { text -> Expression.evaluate(text)?.let(Expression::toPlainText) }),
            error = null,
        )
    }

    fun onAccount(id: Long) = _state.update {
        // Picking the current destination as source swaps them, so a transfer never points to itself.
        val to = if (it.toAccountId == id) it.accountId else it.toAccountId
        it.copy(accountId = id, toAccountId = to, error = null)
    }.also { refreshOptions() }

    fun onToAccount(id: Long) = _state.update {
        val from = if (it.accountId == id) it.toAccountId else it.accountId
        it.copy(toAccountId = id, accountId = from, error = null)
    }.also { refreshOptions() }

    fun onCategory(id: Long) = _state.update { it.copy(categoryId = id, error = null) }

    /** Creates a category of the current kind from the picker and selects it. */
    fun createCategory(name: String, iconKey: String) {
        val kind = _state.value.kind.categoryKind() ?: return
        viewModelScope.launch {
            runCatching { categories.save(Category(name = name, kind = kind, iconKey = iconKey)) }
                .onSuccess { id -> _state.update { it.copy(categoryId = id, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't create category") } }
        }
    }

    /** Combines two main categories (the result counts toward [firstId]) and selects the new one. */
    fun combineCategories(firstId: Long, secondId: Long, name: String) {
        viewModelScope.launch {
            runCatching { categories.combine(firstId, secondId, name) }
                .onSuccess { id -> _state.update { it.copy(categoryId = id, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't combine categories") } }
        }
    }
    fun onDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun onTime(time: LocalTime) = _state.update { it.copy(time = time) }
    fun onNote(note: String) = _state.update { it.copy(note = note.take(200)) }
    init {
        viewModelScope.launch { splits.observePeople().collect { p -> _state.update { it.copy(people = p) } } }
    }

    fun onRepeat(repeat: RepeatSpec?) = _state.update { it.copy(repeat = repeat) }

    fun onSplit(split: SplitDraft?) = _state.update { it.copy(split = split, error = null) }

    fun save() {
        val s = _state.value
        val amount = s.amountPaise
        val error = when {
            amount == null || amount <= 0 -> "Enter an amount"
            s.accountId == null -> "Choose an account"
            s.kind == RecordKind.TRANSFER && (s.toAccountId == null || s.toAccountId == s.accountId) -> "Choose two different accounts"
            s.kind != RecordKind.TRANSFER && s.categoryId == null -> "Choose a category"
            else -> null
        }
        if (error != null) return _state.update { it.copy(error = error) }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val record = Record(
                id = if (mode == EditorMode.EDIT) recordId else 0L,
                kind = s.kind,
                amount = amount!!,
                accountId = s.accountId!!,
                toAccountId = if (s.kind == RecordKind.TRANSFER) s.toAccountId else null,
                categoryId = if (s.kind == RecordKind.TRANSFER) null else s.categoryId,
                occurredAt = LocalDateTime.of(s.date, s.time).atZone(clock.zone).toInstant(),
                note = s.note,
            )
            runCatching {
                val spec = s.splitSpec.takeIf { s.kind == RecordKind.EXPENSE }
                val savedId: Long? = when {
                    // Paid back already: only category, date and note change — on the bill and its parts.
                    s.splitLocked -> { splits.updateBillDetails(recordId, record.categoryId, record.occurredAt, record.note); recordId }
                    spec != null -> {
                        spec.problem?.let { error(it) }
                        splits.saveSplit(record, spec)
                    }
                    // Confirms the due occurrence (and moves the rule to its next date).
                    mode == EditorMode.RECURRING_DUE -> recurring.confirm(fromRuleId, record)
                    s.repeat != null && mode != EditorMode.EDIT -> {
                        val template = RecurringRule(
                            kind = record.kind, amount = record.amount, accountId = record.accountId,
                            toAccountId = record.toAccountId, categoryId = record.categoryId, note = record.note,
                            frequency = s.repeat.frequency, startDate = s.date,
                        )
                        val ruleId = recurring.save(s.repeat.toRule(template, s.date))
                        records.save(record.copy(recurringRuleId = ruleId))
                    }
                    // Was split, now "Don't split": its parts go and it's the whole bill again.
                    mode == EditorMode.EDIT && wasSplit -> {
                        splits.removeSplit(recordId)
                        records.save(record)
                    }
                    else -> records.save(record)
                }
                alerts.checkAfterRecord(YearMonth.from(s.date))
                // A caught payment is now recorded: it leaves To record and its prompt goes.
                if (detectedId > 0) {
                    detectedHandled = true
                    detection.setStatus(detectedId, com.moneyrecords.app.domain.detect.DetectStatus.ADDED, savedId)
                    detectedNotifier.cancel(detectedId)
                    detectedNotifier.refreshSummary()
                }
            }
                .onSuccess {
                    prefs.rememberRecordDefaults(
                        s.kind,
                        RecordDefaults(accountId = record.accountId, toAccountId = record.toAccountId, categoryId = record.categoryId),
                    )
                    _events.send(RecordEditorEvent.Saved)
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save") } }
        }
    }

    fun delete() {
        if (mode != EditorMode.EDIT) return
        viewModelScope.launch {
            val existing = records.get(recordId) ?: return@launch
            // Money paid back for it is still waiting in SBI: deleting would break the balance.
            splits.deleteBlockReason(recordId)?.let { reason ->
                _events.send(RecordEditorEvent.CantDelete(reason))
                return@launch
            }
            val spec = if (existing.isSplit && !splits.hasRepayments(recordId)) splits.splitOf(recordId) else null
            records.delete(recordId)
            _events.send(RecordEditorEvent.Deleted(existing, spec, canUndo = !existing.isSplit || spec != null))
        }
    }
}

private fun RecordKind.categoryKind(): CategoryKind? = when (this) {
    RecordKind.EXPENSE -> CategoryKind.EXPENSE
    RecordKind.INCOME -> CategoryKind.INCOME
    RecordKind.TRANSFER -> null
}

private fun List<Category>.flatten(): List<Category> = flatMap { listOf(it) + it.children }

/**
 * The split being edited. Shares follow the bill equally until one is typed ([custom]); then the
 * typed shares are kept (the editor shows whether they still add up).
 */
data class SplitDraft(
    /** Others' names (null = unnamed); its size + 1 (you) = people. */
    val names: List<String?>,
    /** Typed shares: yours first, then each other person; null = equal split. */
    val custom: List<Long>? = null,
) {
    val people: Int get() = names.size + 1

    fun toSpec(total: Long): SplitSpec {
        val shares = custom?.takeIf { it.size == people } ?: Split.equalShares(total, people)
        return SplitSpec(total, shares.first(), shares.drop(1).mapIndexed { i, a -> SplitPart(names[i], a) })
    }

    companion object {
        fun from(spec: SplitSpec): SplitDraft {
            val shares = listOf(spec.myShare) + spec.parts.map { it.amount }
            val equal = shares.size >= 2 && shares == Split.equalShares(spec.total, shares.size)
            return SplitDraft(names = spec.parts.map { it.person }, custom = if (equal) null else shares)
        }
    }
}
