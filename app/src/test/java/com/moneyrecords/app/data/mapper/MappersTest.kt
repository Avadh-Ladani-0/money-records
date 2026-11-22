package com.moneyrecords.app.data.mapper

import com.moneyrecords.app.data.db.CategoryEntity
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MappersTest {

    private fun cat(id: Long, parent: Long? = null) =
        CategoryEntity(id = id, name = "c$id", kind = CategoryKind.EXPENSE, parentId = parent, iconKey = "other")

    @Test fun `tree nests children under parents`() {
        val tree = listOf(cat(1), cat(2), cat(3, parent = 1), cat(4, parent = 1)).toTree()
        assertEquals(listOf(1L, 2L), tree.map { it.id })
        assertEquals(listOf(3L, 4L), tree.first().children.map { it.id })
    }

    @Test fun `child whose parent is filtered out shows at top level`() {
        val tree = listOf(cat(3, parent = 1)).toTree()
        assertEquals(listOf(3L), tree.map { it.id })
    }

    @Test fun `record round-trips through the entity`() {
        val record = Record(
            id = 7, kind = RecordKind.EXPENSE, amount = 2_349_50, accountId = 1, categoryId = 2,
            occurredAt = Instant.parse("2026-11-22T06:00:00Z"), note = "Groceries",
        )
        assertEquals(record, record.toEntity(createdAt = 1, updatedAt = 2).toDomain())
    }

    @Test fun `recurring rule round-trips through the entity`() {
        val rule = RecurringRule(
            id = 3, kind = RecordKind.EXPENSE, amount = 15_000_00, accountId = 1, categoryId = 7,
            note = "Rent", frequency = Frequency.MONTHLY, interval = 1,
            startDate = LocalDate.of(2026, 12, 1), endDate = LocalDate.of(2027, 11, 1),
        )
        assertEquals(rule, rule.toEntity().toDomain())
    }
}
