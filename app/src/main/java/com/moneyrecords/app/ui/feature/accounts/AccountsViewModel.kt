package com.moneyrecords.app.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsState(
    val loading: Boolean = true,
    val active: List<Account> = emptyList(),
    val archived: List<Account> = emptyList(),
    val total: Long = 0,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(repository: AccountRepository) : ViewModel() {
    val state: StateFlow<AccountsState> = repository.observeAccounts(includeArchived = true)
        .map { all ->
            val active = all.filterNot { it.archived }
            AccountsState(loading = false, active = active, archived = all.filter { it.archived }, total = active.sumOf { it.balance })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}

fun AccountType.label(): String = when (this) {
    AccountType.CASH -> "Cash"
    AccountType.BANK -> "Bank"
    AccountType.CARD -> "Card"
    AccountType.WALLET -> "Wallet"
    AccountType.OTHER -> "Other"
}

fun AccountType.defaultIconKey(): String = when (this) {
    AccountType.CASH -> "cash"
    AccountType.BANK -> "bank"
    AccountType.CARD -> "card"
    AccountType.WALLET, AccountType.OTHER -> "wallet"
}

data class AccountEditorState(
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    /** As typed; may start with "-" (e.g. a credit card that starts in debt). */
    val openingText: String = "",
    val archived: Boolean = false,
    val error: String? = null,
) {
    val openingPaise: Long?
        get() {
            val text = openingText.trim()
            if (text.isEmpty()) return 0L
            val negative = text.startsWith("-") || text.startsWith(Money.MINUS)
            val value = Money.parse(text.removePrefix("-").removePrefix(Money.MINUS)) ?: return null
            return if (negative) -value else value
        }
    val canSave: Boolean get() = name.isNotBlank() && openingPaise != null
}

sealed interface AccountEditorEvent {
    data object Saved : AccountEditorEvent
    data class Removed(val outcome: DeleteOutcome) : AccountEditorEvent
}

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: AccountRepository,
) : ViewModel() {
    private val accountId: Long = handle.get<Long>(RouteArgs.ACCOUNT_ID) ?: 0L
    private val _state = MutableStateFlow(AccountEditorState(isNew = accountId == 0L, loading = accountId != 0L))
    val state: StateFlow<AccountEditorState> = _state.asStateFlow()
    private val _events = Channel<AccountEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var original: Account? = null

    init {
        if (accountId != 0L) viewModelScope.launch {
            val account = repository.get(accountId)
            original = account
            _state.update {
                if (account == null) it.copy(loading = false, error = "Account not found")
                else it.copy(
                    loading = false,
                    name = account.name,
                    type = account.type,
                    openingText = if (account.openingBalance == 0L) "" else
                        (if (account.openingBalance < 0) "-" else "") + Money.format(account.openingBalance, symbol = "", sign = Money.Sign.NONE),
                    archived = account.archived,
                )
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onType(value: AccountType) = _state.update { it.copy(type = value) }
    fun onOpening(value: String) = _state.update { it.copy(openingText = value, error = null) }

    fun save() {
        val s = _state.value
        val opening = s.openingPaise ?: return _state.update { it.copy(error = "Enter a valid amount") }
        viewModelScope.launch {
            runCatching {
                repository.save(
                    Account(
                        id = accountId,
                        name = s.name,
                        type = s.type,
                        openingBalance = opening,
                        // Keep a custom icon if the type is unchanged; otherwise follow the type.
                        iconKey = original?.takeIf { it.type == s.type }?.iconKey ?: s.type.defaultIconKey(),
                        archived = s.archived,
                        sortOrder = original?.sortOrder ?: 0,
                    ),
                )
            }.onSuccess { _events.send(AccountEditorEvent.Saved) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't save") } }
        }
    }

    fun toggleArchived() {
        if (accountId == 0L) return
        viewModelScope.launch {
            val archived = !_state.value.archived
            repository.setArchived(accountId, archived)
            _state.update { it.copy(archived = archived) }
        }
    }

    fun delete() {
        if (accountId == 0L) return
        viewModelScope.launch {
            _events.send(AccountEditorEvent.Removed(repository.deleteOrArchive(accountId)))
        }
    }
}
