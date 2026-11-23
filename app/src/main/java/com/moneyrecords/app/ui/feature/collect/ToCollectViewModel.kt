package com.moneyrecords.app.ui.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.SplitRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.domain.split.AwaitingMove
import com.moneyrecords.app.domain.split.OpenPart
import com.moneyrecords.app.domain.split.SplitMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/** Bills grouped by who owes them ("Others" when no name was given). */
data class PersonGroup(val name: String, val total: Long, val parts: List<OpenPart>) {
    /** Unnamed parts can't be paid "by a person" — there's no one to match. */
    val isNamed: Boolean get() = name != OTHERS
}

/** An earlier month's bills that weren't fully paid back. */
data class MonthGroup(val month: YearMonth, val total: Long, val people: List<PersonGroup>)

const val OTHERS = "Others"

data class ToCollectState(
    val loading: Boolean = true,
    /** This month's bills, by person. */
    val thisMonth: List<PersonGroup> = emptyList(),
    val thisMonthTotal: Long = 0,
    /** Earlier months, newest first — still owed, still actionable. */
    val earlier: List<MonthGroup> = emptyList(),
    val earlierTotal: Long = 0,
    val awaiting: List<AwaitingMove> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val repaymentAccountId: Long? = null,
) {
    /** Where a repayment from [payerAccountId] most likely arrived: the remembered account, else any other. */
    fun defaultDestination(payerAccountId: Long): Long? =
        repaymentAccountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull { it.id != payerAccountId }?.id
            ?: payerAccountId

    /** Everything [person] owes, across this month and earlier ones. */
    fun owedBy(person: String): Long =
        (thisMonth + earlier.flatMap { it.people }).filter { it.name.equals(person, ignoreCase = true) }.sumOf { it.total }
}

fun groupByPerson(parts: List<OpenPart>): List<PersonGroup> =
    parts.groupBy { it.person?.trim()?.ifBlank { null } ?: OTHERS }
        .map { (name, ps) -> PersonGroup(name, ps.sumOf { it.outstanding }, ps) }
        .sortedWith(compareBy<PersonGroup> { it.name == OTHERS }.thenByDescending { it.total })

@HiltViewModel
class ToCollectViewModel @Inject constructor(
    private val splits: SplitRepository,
    accounts: AccountRepository,
    private val prefs: UserPrefsRepository,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<ToCollectState> = combine(
        splits.observeOpen(),
        splits.observeAwaitingMove(),
        accounts.observeAccounts(),
        prefs.observeRepaymentAccount(),
    ) { open, awaiting, accountList, repayment ->
        val (current, older) = SplitMath.byMonth(open, YearMonth.now(clock), clock.zone)
        ToCollectState(
            loading = false,
            thisMonth = groupByPerson(current),
            thisMonthTotal = current.sumOf { it.outstanding },
            earlier = older.map { (month, parts) -> MonthGroup(month, parts.sumOf { it.outstanding }, groupByPerson(parts)) },
            earlierTotal = older.sumOf { (_, parts) -> parts.sumOf { it.outstanding } },
            awaiting = awaiting,
            accounts = accountList,
            repaymentAccountId = repayment,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToCollectState())

    fun receive(part: OpenPart, amount: Long, accountId: Long, onDone: (String?) -> Unit) = run(onDone) {
        splits.receive(part.legId, amount, accountId)
        prefs.setRepaymentAccount(accountId)
    }

    fun receiveFromPerson(person: String, amount: Long, accountId: Long, onDone: (String?) -> Unit) = run(onDone) {
        splits.receiveFromPerson(person, amount, accountId)
        prefs.setRepaymentAccount(accountId)
    }

    private fun run(onDone: (String?) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch { onDone(runCatching { block() }.exceptionOrNull()?.message) }
    }

    /** Moves [amount] of collected money back to the account that paid; [onMoved] gets an undo. */
    fun move(move: AwaitingMove, amount: Long, onMoved: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val token = runCatching { splits.moveCollected(move.payerAccountId, move.heldInAccountId, amount) }.getOrNull() ?: return@launch
            onMoved { viewModelScope.launch { splits.undoMove(token) } }
        }
    }
}
