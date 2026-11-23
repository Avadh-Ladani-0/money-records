package com.moneyrecords.app.data.repository

import androidx.room.withTransaction
import com.moneyrecords.app.data.db.AccountEntity
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.RecordEntity
import com.moneyrecords.app.data.mapper.toEntity
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.repository.SplitRepository
import com.moneyrecords.app.domain.split.AwaitingMove
import com.moneyrecords.app.domain.split.OpenPart
import com.moneyrecords.app.domain.split.Owed
import com.moneyrecords.app.domain.split.SplitMath
import com.moneyrecords.app.domain.split.SplitPart
import com.moneyrecords.app.domain.split.SplitSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Split bills as plain records:
 * - your share: an EXPENSE (amount = your share, `splitTotal` = the whole bill). With a ₹0 share it
 *   is kept only as the bill's anchor and hidden from lists;
 * - each friend's part: an EXPENSE in the same category and account, `splitOf` = your share —
 *   real spending until it comes back;
 * - a repayment: a TRANSFER from the hidden "To collect" account into where it arrived,
 *   `splitOf` = the part, `awaitingMove` until it's moved back to the account that paid.
 * Moving folds repayments into their parts (the part shrinks, the repayment goes), as if the money
 * had come straight back to the account that paid.
 */
@Singleton
class RoomSplitRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val clock: Clock,
) : SplitRepository {
    private val records = db.recordDao()
    private val accounts = db.accountDao()

    /** Source of repayments; hidden everywhere and excluded from totals. */
    private suspend fun collectAccountId(): Long =
        accounts.systemAccount()?.id ?: accounts.insert(
            AccountEntity(
                name = "To collect", type = AccountType.OTHER, openingBalance = 0, iconKey = "wallet",
                archived = false, sortOrder = Int.MAX_VALUE, createdAt = clock.millis(), system = true,
            ),
        )

    override suspend fun saveSplit(expense: Record, spec: SplitSpec): Long = db.withTransaction {
        require(spec.problem == null) { spec.problem!! }
        require(expense.kind == RecordKind.EXPENSE) { "Only expenses can be split" }
        val now = clock.millis()
        val mine = expense.copy(amount = spec.myShare, splitTotal = spec.total, splitOf = null, person = null, awaitingMove = false)
        val id = if (mine.id == 0L) {
            records.insert(mine.toEntity(createdAt = now, updatedAt = now))
        } else {
            require(!hasRepayments(mine.id)) { "Something was already paid back — this split can't be changed" }
            val existing = requireNotNull(records.get(mine.id)) { "Record not found" }
            records.update(mine.toEntity(createdAt = existing.createdAt, updatedAt = now))
            records.deleteChildren(mine.id)
            mine.id
        }
        spec.parts.forEach { part ->
            records.insert(
                RecordEntity(
                    kind = RecordKind.EXPENSE, amount = part.amount, accountId = expense.accountId,
                    categoryId = expense.categoryId, occurredAt = expense.occurredAt.toEpochMilli(),
                    note = expense.note.trim(), createdAt = now, updatedAt = now,
                    splitOf = id, person = part.person?.trim()?.ifBlank { null },
                ),
            )
        }
        id
    }

    override suspend fun removeSplit(expenseId: Long) = db.withTransaction {
        require(!hasRepayments(expenseId)) { "Something was already paid back — this split can't be removed" }
        val bill = records.get(expenseId) ?: return@withTransaction
        val total = bill.splitTotal ?: return@withTransaction
        records.deleteChildren(expenseId)
        records.update(bill.copy(amount = total, splitTotal = null, updatedAt = clock.millis()))
    }

    override suspend fun splitOf(expenseId: Long): SplitSpec? {
        val bill = records.get(expenseId) ?: return null
        val total = bill.splitTotal ?: return null
        val parts = records.children(expenseId).filter { it.kind == RecordKind.EXPENSE }.map { SplitPart(it.person, it.amount) }
        return SplitSpec(total = total, myShare = bill.amount, parts = parts)
    }

    override suspend fun hasRepayments(expenseId: Long): Boolean {
        val bill = records.get(expenseId) ?: return false
        val total = bill.splitTotal ?: return false
        val parts = records.children(expenseId).filter { it.kind == RecordKind.EXPENSE }
        // Parts shrink when repayments are moved, so a smaller sum also means "paid back".
        return parts.sumOf { it.amount } != total - bill.amount || parts.any { records.children(it.id).isNotEmpty() }
    }

    override suspend fun updateBillDetails(expenseId: Long, categoryId: Long?, occurredAt: Instant, note: String) = db.withTransaction {
        val bill = requireNotNull(records.get(expenseId)) { "Record not found" }
        val now = clock.millis()
        records.update(bill.copy(categoryId = categoryId, occurredAt = occurredAt.toEpochMilli(), note = note.trim(), updatedAt = now))
        records.updatePartDetails(expenseId, categoryId, occurredAt.toEpochMilli(), note.trim(), now)
    }

    private suspend fun owedOn(leg: RecordEntity): Long = leg.amount - records.children(leg.id).sumOf { it.amount }

    override suspend fun receive(legId: Long, amount: Long, accountId: Long): Unit = db.withTransaction {
        val leg = requireNotNull(records.get(legId)) { "Not found" }
        val owed = owedOn(leg)
        require(amount in 1..owed) { "At most ${Money.format(owed)} is owed on this bill" }
        if (accountId == leg.accountId) {
            // Paid straight back into the account that paid: settled at once, nothing to move.
            fold(leg, amount)
        } else {
            val now = clock.millis()
            records.insert(
                RecordEntity(
                    kind = RecordKind.TRANSFER, amount = amount, accountId = collectAccountId(), toAccountId = accountId,
                    occurredAt = now, note = "", createdAt = now, updatedAt = now,
                    splitOf = legId, person = leg.person, awaitingMove = true,
                ),
            )
        }
        Unit
    }

    override suspend fun receiveFromPerson(person: String, amount: Long, accountId: Long): Unit = db.withTransaction {
        val open = observeOpen().first()
            .filter { it.person.equals(person, ignoreCase = true) }
            .sortedWith(compareBy<OpenPart> { it.occurredAtMillis }.thenBy { it.legId })
        val total = open.sumOf { it.outstanding }
        require(amount in 1..total) { "At most ${Money.format(total)} is owed by $person" }
        SplitMath.allocate(amount, open.map { Owed(it.legId, it.outstanding) }).forEach { receive(it.id, it.amount, accountId) }
    }

    override suspend fun unreceive(repaymentId: Long) {
        val r = records.get(repaymentId) ?: return
        require(r.awaitingMove) { "Only a repayment that hasn't been moved can be removed" }
        records.delete(repaymentId)
    }

    /** Shrinks a part by a repaid [amount]; a part paid in full is removed. */
    private suspend fun fold(leg: RecordEntity, amount: Long) {
        val left = leg.amount - amount
        if (left <= 0) {
            records.deleteGrandchildrenOfLeg(leg.id)
            records.delete(leg.id)
        } else {
            records.setAmount(leg.id, left)
        }
    }

    override fun observeOpen(): Flow<List<OpenPart>> = records.observeOpenParts().map { rows ->
        rows.filter { it.outstanding > 0 }.map {
            OpenPart(
                legId = it.legId, person = it.person, payerAccountId = it.payerId, payerName = it.payerName,
                expenseId = it.expenseId, title = it.note.ifBlank { it.categoryName ?: "Bill" }, iconKey = it.categoryIconKey,
                occurredAtMillis = it.occurredAt, billTotal = it.billTotal, outstanding = it.outstanding, waiting = it.waiting,
            )
        }
    }

    override fun observeAwaitingMove(): Flow<List<AwaitingMove>> = records.observeAwaitingMove().map { rows ->
        rows.map { AwaitingMove(it.payerId, it.payerName, it.heldIn, it.heldInName, it.total, it.count) }
    }

    /** Every row a move touched, as it was before — undo writes them back. */
    private data class MoveUndo(val before: List<RecordEntity>)

    override suspend fun moveCollected(payerId: Long, heldInId: Long, amount: Long): Any = db.withTransaction {
        val waiting = records.awaitingFor(payerId, heldInId)
        require(amount in 1..waiting.sumOf { it.amount }) { "Enter up to ${Money.format(waiting.sumOf { it.amount })}" }
        val before = mutableListOf<RecordEntity>()
        SplitMath.allocate(amount, waiting.map { Owed(it.id, it.amount) }).forEach { take ->
            val repayment = waiting.first { it.id == take.id }
            val leg = records.get(repayment.splitOf ?: return@forEach) ?: return@forEach
            before += repayment
            if (before.none { it.id == leg.id }) before += leg
            if (take.amount == repayment.amount) {
                records.delete(repayment.id)
            } else {
                // Only part of this repayment was moved: the rest keeps waiting.
                records.setAmount(repayment.id, repayment.amount - take.amount)
            }
            val current = records.get(leg.id) ?: return@forEach
            val left = current.amount - take.amount
            if (left <= 0) records.delete(leg.id) else records.setAmount(leg.id, left)
        }
        MoveUndo(before)
    }

    override suspend fun undoMove(token: Any): Unit = db.withTransaction {
        val undo = token as? MoveUndo ?: return@withTransaction
        // Parts first, then their repayments.
        undo.before.sortedBy { if (it.kind == RecordKind.EXPENSE) 0 else 1 }.forEach { row ->
            if (records.get(row.id) == null) records.insert(row) else records.update(row)
        }
    }

    override suspend fun deleteBlockReason(recordId: Long): String? {
        val waiting = records.waitingFor(recordId)
        if (waiting.isEmpty()) return null
        val held = waiting.mapNotNull { it.toAccountId }.distinct().mapNotNull { accounts.get(it)?.name }.joinToString(" and ")
        return "${Money.format(waiting.sumOf { it.amount })} paid back for this bill is waiting in $held. " +
            "Move it first — deleting now would make $held disagree with your bank."
    }

    override fun observePeople(): Flow<List<String>> = records.observePeople()
}
