package com.moneyrecords.app.ui.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.time.MonthRange
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.YearMonth
import com.moneyrecords.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Money into and out of one account this month (transfers count on both sides). */
data class AccountFlow(val inflow: Long = 0, val outflow: Long = 0)

data class AccountsState(
    val loading: Boolean = true,
    val active: List<Account> = emptyList(),
    val archived: List<Account> = emptyList(),
    val total: Long = 0,
    /** Sum of balances above zero. */
    val assets: Long = 0,
    /** Sum of balances below zero, as a positive figure (cards in debt). */
    val owed: Long = 0,
    val flows: Map<Long, AccountFlow> = emptyMap(),
    /** Owed to you by friends (bills you split). */
    val toCollect: Long = 0,
)

/** Per-account in/out for the given records: income and transfers in vs expenses and transfers out. */
fun accountFlows(records: List<RecordDetails>): Map<Long, AccountFlow> {
    val inflow = mutableMapOf<Long, Long>()
    val outflow = mutableMapOf<Long, Long>()
    records.forEach { d ->
        val r = d.record
        when (r.kind) {
            RecordKind.INCOME -> inflow.merge(r.accountId, r.amount, Long::plus)
            RecordKind.EXPENSE -> outflow.merge(r.accountId, r.amount, Long::plus)
            RecordKind.TRANSFER -> {
                outflow.merge(r.accountId, r.amount, Long::plus)
                r.toAccountId?.let { inflow.merge(it, r.amount, Long::plus) }
            }
        }
    }
    return (inflow.keys + outflow.keys).associateWith { AccountFlow(inflow[it] ?: 0, outflow[it] ?: 0) }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    repository: AccountRepository,
    records: RecordRepository,
    splits: com.moneyrecords.app.domain.repository.SplitRepository,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)

    val state: StateFlow<AccountsState> = combine(
        repository.observeAccounts(includeArchived = true),
        records.observeRecords(RecordFilter(range = MonthRange.of(month, clock.zone), limit = 5_000)),
        splits.observeOpen(),
    ) { all, monthRecords, open ->
        val active = all.filterNot { it.archived }
        AccountsState(
            loading = false,
            active = active,
            archived = all.filter { it.archived },
            total = active.sumOf { it.balance },
            assets = active.filter { it.balance > 0 }.sumOf { it.balance },
            owed = -active.filter { it.balance < 0 }.sumOf { it.balance },
            flows = accountFlows(monthRecords),
            toCollect = open.sumOf { it.outstanding },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}

fun AccountType.label(): String = when (this) {
    AccountType.CASH -> "Cash"
    AccountType.BANK -> "Bank"
    AccountType.CARD -> "Card"
    AccountType.WALLET -> "Wallet"
    AccountType.UPI_LITE -> "UPI Lite"
    AccountType.OTHER -> "Other"
}

fun AccountType.defaultIconKey(): String = when (this) {
    AccountType.CASH -> "cash"
    AccountType.BANK -> "bank"
    AccountType.CARD -> "card"
    AccountType.WALLET, AccountType.OTHER -> "wallet"
    AccountType.UPI_LITE -> "lite"
}

data class AccountEditorState(
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    /** As typed; may start with "-" (e.g. a credit card that starts in debt). */
    val openingText: String = "",
    val archived: Boolean = false,
    /** Last 4 digits of the bank account / card, to recognise its SMS alerts. */
    val digits: String = "",
    /** UPI Lite: the app it's enabled in and the bank account funding it. */
    val liteApp: String? = null,
    val fundingAccountId: Long? = null,
    /** Bank accounts that can fund UPI Lite. */
    val banks: List<Account> = emptyList(),
    /** Another UPI Lite account exists (only one per phone). */
    val otherLiteExists: Boolean = false,
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
    val canSave: Boolean
        get() = name.isNotBlank() && openingPaise != null &&
            (type != AccountType.UPI_LITE || (liteApp != null && fundingAccountId != null))
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
        viewModelScope.launch {
            val all = repository.observeAccounts().first()
            _state.update { s ->
                s.copy(
                    banks = all.filter { it.type == AccountType.BANK && it.id != accountId },
                    otherLiteExists = all.any { it.type == AccountType.UPI_LITE && it.id != accountId },
                )
            }
        }
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
                    digits = account.digits.orEmpty(),
                    liteApp = account.liteApp,
                    fundingAccountId = account.fundingAccountId,
                )
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onType(value: AccountType) = _state.update { it.copy(type = value) }
    fun onLiteApp(pkg: String) = _state.update { it.copy(liteApp = pkg) }
    fun onFunding(id: Long) = _state.update { it.copy(fundingAccountId = id) }
    fun onDigits(value: String) = _state.update { it.copy(digits = value.filter(Char::isDigit).take(4)) }
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
                        digits = s.digits.ifBlank { null },
                        liteApp = s.liteApp,
                        fundingAccountId = s.fundingAccountId,
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
