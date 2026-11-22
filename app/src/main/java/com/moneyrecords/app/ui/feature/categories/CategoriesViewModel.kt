package com.moneyrecords.app.ui.feature.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesState(
    val loading: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<Category> = emptyList(),
    /** Archived categories (and archived subcategories of active parents), flattened. */
    val archived: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repository: CategoryRepository) : ViewModel() {
    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val _message = Channel<String>(Channel.BUFFERED)
    /** One-off messages (e.g. a combine that was rejected). */
    val messages = _message.receiveAsFlow()

    val state: StateFlow<CategoriesState> = kind.flatMapLatest { k ->
        repository.observeTree(k, includeArchived = true).map { tree ->
            CategoriesState(
                loading = false,
                kind = k,
                active = tree.filterNot { it.archived }.map { p -> p.copy(children = p.children.filterNot { it.archived }) },
                archived = tree.flatMap { p -> if (p.archived) listOf(p) else p.children.filter { it.archived } },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState())

    fun setKind(value: CategoryKind) { kind.value = value }

    fun combine(firstId: Long, secondId: Long, name: String) {
        viewModelScope.launch {
            runCatching { repository.combine(firstId, secondId, name) }
                .onSuccess { _message.send("Created “$name”") }
                .onFailure { e -> _message.send(e.message ?: "Couldn't combine categories") }
        }
    }
}

data class CategoryEditorState(
    val isNew: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val name: String = "",
    val iconKey: String = "other",
    val parentId: Long? = null,
    /** Top-level categories this one may be nested under. */
    val parentOptions: List<Category> = emptyList(),
    /** A category with subcategories can't itself become a subcategory. */
    val hasChildren: Boolean = false,
    val archived: Boolean = false,
    val error: String? = null,
    /** New category being made by combining two main categories. */
    val combineMode: Boolean = false,
    val firstId: Long? = null,
    val secondId: Long? = null,
) {
    /** Editing an existing combined category: only name / archive / delete can change. */
    val isCombo: Boolean get() = !isNew && ComboIcon.isCombo(iconKey)

    /** Main categories that can be combined (not combined themselves). */
    val combinable: List<Category> get() = parentOptions.filterNot { ComboIcon.isCombo(it.iconKey) }
    val first: Category? get() = parentOptions.firstOrNull { it.id == firstId }
    val second: Category? get() = parentOptions.firstOrNull { it.id == secondId }

    /** The parent a combined category counts in (for display). */
    val comboParentName: String? get() = parentOptions.firstOrNull { it.id == parentId }?.name

    val canSave: Boolean
        get() = name.isNotBlank() && (!combineMode || (firstId != null && secondId != null && firstId != secondId))
}

sealed interface CategoryEditorEvent {
    data object Saved : CategoryEditorEvent
    data class Removed(val outcome: DeleteOutcome) : CategoryEditorEvent
}

@HiltViewModel
class CategoryEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val categoryId: Long = handle.get<Long>(RouteArgs.CATEGORY_ID) ?: 0L
    private val initialKind = handle.get<String>(RouteArgs.KIND)
        ?.let { runCatching { CategoryKind.valueOf(it) }.getOrNull() } ?: CategoryKind.EXPENSE

    private val _state = MutableStateFlow(CategoryEditorState(isNew = categoryId == 0L, kind = initialKind))
    val state: StateFlow<CategoryEditorState> = _state.asStateFlow()
    private val _events = Channel<CategoryEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var original: Category? = null

    init {
        viewModelScope.launch {
            val existing = if (categoryId != 0L) repository.get(categoryId) else null
            original = existing
            val kind = existing?.kind ?: initialKind
            if (existing != null) {
                _state.update {
                    it.copy(kind = kind, name = existing.name, iconKey = existing.iconKey, parentId = existing.parentId, archived = existing.archived)
                }
            }
            repository.observeTree(kind).collect { tree ->
                _state.update { s ->
                    s.copy(
                        parentOptions = tree.filter { it.id != categoryId },
                        hasChildren = tree.firstOrNull { it.id == categoryId }?.children?.isNotEmpty() == true,
                    )
                }
            }
        }
    }

    /** True once the user types a name; until then a combined name follows the picks ("First-Second"). */
    private var nameEdited = false

    fun onName(value: String) {
        nameEdited = true
        _state.update { it.copy(name = value.take(40), error = null) }
    }

    fun setCombineMode(enabled: Boolean) = _state.update {
        if (!it.isNew) it else it.copy(combineMode = enabled, error = null).withAutoName()
    }
    fun onFirst(id: Long) = _state.update { it.copy(firstId = id, secondId = it.secondId.takeIf { s -> s != id }, error = null).withAutoName() }
    fun onSecond(id: Long) = _state.update { it.copy(secondId = id, firstId = it.firstId.takeIf { f -> f != id }, error = null).withAutoName() }
    fun swap() = _state.update { it.copy(firstId = it.secondId, secondId = it.firstId).withAutoName() }

    private fun CategoryEditorState.withAutoName(): CategoryEditorState {
        if (nameEdited || !combineMode) return this
        val a = first?.name ?: return copy(name = "")
        val b = second?.name ?: return copy(name = a)
        return copy(name = "$a-$b")
    }
    fun onIcon(key: String) = _state.update { it.copy(iconKey = key) }
    fun onParent(id: Long?) = _state.update { it.copy(parentId = id) }

    fun save() {
        val s = _state.value
        if (s.isNew && s.combineMode) {
            val first = s.firstId ?: return
            val second = s.secondId ?: return
            viewModelScope.launch {
                runCatching { repository.combine(first, second, s.name) }
                    .onSuccess { _events.send(CategoryEditorEvent.Saved) }
                    .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't combine") } }
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.save(
                    Category(
                        id = categoryId,
                        name = s.name,
                        kind = s.kind,
                        parentId = s.parentId,
                        iconKey = s.iconKey,
                        archived = s.archived,
                        sortOrder = original?.sortOrder ?: 0,
                    ),
                )
            }.onSuccess { _events.send(CategoryEditorEvent.Saved) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't save") } }
        }
    }

    fun toggleArchived() {
        if (categoryId == 0L) return
        viewModelScope.launch {
            val archived = !_state.value.archived
            repository.setArchived(categoryId, archived)
            _state.update { it.copy(archived = archived) }
        }
    }

    fun delete() {
        if (categoryId == 0L) return
        viewModelScope.launch { _events.send(CategoryEditorEvent.Removed(repository.deleteOrArchive(categoryId))) }
    }
}
