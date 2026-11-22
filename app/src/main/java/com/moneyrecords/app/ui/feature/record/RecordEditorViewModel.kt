package com.moneyrecords.app.ui.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.calc.Expression
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
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

enum class EditorMode { NEW, EDIT, DUPLICATE }

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
    val error: String? = null,
    val saving: Boolean = false,
) {
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
    data class Deleted(val record: Record) : RecordEditorEvent
}

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val records: RecordRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val prefs: UserPrefsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val recordId: Long = handle.get<Long>(RouteArgs.RECORD_ID) ?: 0L
    private val duplicateOf: Long = handle.get<Long>(RouteArgs.DUPLICATE_OF) ?: 0L
    private val mode = when {
        recordId != 0L -> EditorMode.EDIT
        duplicateOf != 0L -> EditorMode.DUPLICATE
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
        val source = when (mode) {
            EditorMode.EDIT -> records.get(recordId)
            EditorMode.DUPLICATE -> records.get(duplicateOf)
            EditorMode.NEW -> null
        }
        if (source != null) {
            val at = LocalDateTime.ofInstant(source.occurredAt, clock.zone)
            _state.update {
                it.copy(
                    kind = source.kind,
                    amountText = Expression.toPlainText(BigDecimal(source.amount).movePointLeft(2)),
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
            applyDefaults(RecordKind.EXPENSE)
        }
        loadRecents(_state.value.kind)
        _state.update { it.copy(loading = false) }
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
            runCatching { records.save(record) }
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
            records.delete(recordId)
            _events.send(RecordEditorEvent.Deleted(existing))
        }
    }
}

private fun RecordKind.categoryKind(): CategoryKind? = when (this) {
    RecordKind.EXPENSE -> CategoryKind.EXPENSE
    RecordKind.INCOME -> CategoryKind.INCOME
    RecordKind.TRANSFER -> null
}

private fun List<Category>.flatten(): List<Category> = flatMap { listOf(it) + it.children }
