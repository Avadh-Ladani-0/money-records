package com.moneyrecords.app.data.repository

import androidx.room.withTransaction
import com.moneyrecords.app.data.db.DetectedEntity
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.domain.detect.DetectSource
import com.moneyrecords.app.domain.detect.DetectStatus
import com.moneyrecords.app.domain.detect.Detected
import com.moneyrecords.app.domain.detect.DetectionRules
import com.moneyrecords.app.domain.detect.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to an incoming alert. [notify] = show (or refresh) its prompt. */
data class IngestResult(val item: Detected, val notify: Boolean)

/** Caught payments and credits, merged across sources so one payment is one item. */
@Singleton
class RoomDetectionRepository @Inject constructor(
    private val db: MoneyRecordsDatabase,
    private val settings: com.moneyrecords.app.detect.DetectionSettings,
    private val clock: Clock,
) : com.moneyrecords.app.domain.detect.DetectionRepository {
    private val dao = db.detectedDao()

    suspend fun ingest(incoming: Detected): IngestResult = db.withTransaction {
        val window = DetectionRules.WIDGET_WINDOW_MS
        val candidates = (dao.around(incoming.occurredAt - window, incoming.occurredAt + window).map { it.toDomain() } +
            listOfNotNull(incoming.ref?.let { dao.byRef(it)?.toDomain() })).distinctBy { it.id }

        // Same payment seen again (SMS + app notification, or the alert for a pay-widget tap).
        candidates.firstOrNull { DetectionRules.sameMovement(it, incoming) }?.let { existing ->
            val merged = DetectionRules.merge(existing, incoming)
            if (merged != existing) dao.update(merged.toEntity(clock.millis()))
            val open = existing.status == DetectStatus.NEW || existing.status == DetectStatus.LATER
            return@withTransaction IngestResult(merged, notify = open && merged != existing)
        }
        // Out of one of your accounts and into another: one transfer, not two items.
        candidates.firstOrNull {
            (it.status == DetectStatus.NEW || it.status == DetectStatus.LATER) && DetectionRules.transferPair(it, incoming)
        }?.let { other ->
            val (out, into) = if (other.direction == Direction.OUT) other to incoming else incoming to other
            val transfer = other.copy(
                direction = Direction.OUT, accountId = out.accountId, toAccountId = into.accountId,
                ref = other.ref ?: incoming.ref, counterparty = null,
            )
            dao.update(transfer.toEntity(clock.millis()))
            return@withTransaction IngestResult(transfer, notify = true)
        }
        val id = dao.insert(incoming.toEntity(clock.millis()))
        IngestResult(incoming.copy(id = id), notify = true)
    }

    override suspend fun get(id: Long): Detected? = dao.get(id)?.toDomain()

    suspend fun update(d: Detected) {
        val existing = dao.get(d.id) ?: return
        dao.update(d.toEntity(existing.createdAt))
    }

    override fun observeOpen(): Flow<List<Detected>> = dao.observeOpen().map { rows -> rows.map { it.toDomain() } }

    fun observeLog(): Flow<List<Detected>> = dao.observeLog().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setStatus(id: Long, status: DetectStatus, recordId: Long?) = dao.setStatus(id, status.name, recordId)

    /** A record that may already be this one: same amount and account within a day. */
    suspend fun looksRecorded(d: Detected): Boolean {
        if (d.amount <= 0 || d.accountId == null) return false
        val day = 24 * 60 * 60_000L
        val kind = when {
            d.isTransfer -> "TRANSFER"
            d.direction == Direction.OUT -> "EXPENSE"
            else -> "INCOME"
        }
        return db.recordDao().countSimilar(kind, d.amount, d.accountId, d.occurredAt - day, d.occurredAt + day) > 0
    }

    /** The category last used for [counterparty] (we save the payee as the note). */
    override suspend fun categoryFor(counterparty: String?, direction: Direction): Long? {
        if (counterparty.isNullOrBlank()) return null
        return db.recordDao().lastCategoryForNote(counterparty, if (direction == Direction.OUT) "EXPENSE" else "INCOME")
    }

    private suspend fun lite() = db.accountDao().liteAccount()

    override suspend fun accountsFor(origin: String): List<Long> {
        val prefs = settings.current()
        val lite = lite()?.takeIf { it.liteApp == origin }
        return (listOfNotNull(lite?.id) + listOfNotNull(prefs.appUsual[origin]) + prefs.appLinks[origin].orEmpty().sorted()).distinct()
    }

    override suspend fun guessAccount(d: Detected): Long? {
        d.accountId?.let { return it }
        val prefs = settings.current()
        val lite = lite()
        return DetectionRules.accountForApp(
            d.origin, d.direction, d.amount, lite?.id, lite?.liteApp, prefs.appLinks[d.origin].orEmpty(), prefs.appUsual[d.origin],
        )
    }

    /**
     * A UPI Lite top-up caught before the UPI Lite account existed is fixed up: a transfer from its
     * funding bank into UPI Lite.
     */
    override suspend fun refresh(id: Long): Detected? {
        val d = get(id) ?: return null
        val lite = lite() ?: return d
        if (!d.upiLite || d.direction != Direction.OUT || d.toAccountId != null || d.status !in setOf(DetectStatus.NEW, DetectStatus.LATER)) return d
        val from = d.accountId?.takeIf { it != lite.id } ?: lite.fundingAccountId ?: return d
        val fixed = d.copy(accountId = from, toAccountId = lite.id, counterparty = null)
        update(fixed)
        return fixed
    }

    /** [refresh] for everything still waiting (run at start-up). */
    suspend fun refreshOpen() = dao.openNow().forEach { refresh(it.id) }

    /** Pay-widget taps that never got an alert stay as items; handled ones are pruned after a month. */
    suspend fun prune() = dao.prune(clock.millis() - 31L * 24 * 60 * 60_000)
}

fun DetectedEntity.toDomain() = Detected(
    id = id, source = DetectSource.valueOf(source), origin = origin, direction = Direction.valueOf(direction),
    amount = amount, accountId = accountId, toAccountId = toAccountId, accountDigits = accountDigits,
    counterparty = counterparty, ref = ref, upiLite = upiLite, occurredAt = occurredAt, text = text,
    status = DetectStatus.valueOf(status), recordId = recordId,
)

fun Detected.toEntity(now: Long) = DetectedEntity(
    id = id, source = source.name, origin = origin, direction = direction.name, amount = amount,
    accountId = accountId, toAccountId = toAccountId, accountDigits = accountDigits, counterparty = counterparty,
    ref = ref, upiLite = upiLite, occurredAt = occurredAt, text = text.take(300), status = status.name,
    recordId = recordId, createdAt = now,
)
