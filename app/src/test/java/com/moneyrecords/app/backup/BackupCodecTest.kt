package com.moneyrecords.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val file = BackupFile(
        appVersion = "0.1.0",
        dbVersion = 3,
        createdAt = 1_795_000_000_000,
        data = BackupData(
            accounts = listOf(AccountRow(1, "SBI", "BANK", 0, "bank", false, 0, 1)),
            categories = listOf(
                CategoryRow(11, "Snacks", "EXPENSE", parentId = 10, iconKey = "food", archived = false, sortOrder = 0),
                CategoryRow(10, "Food", "EXPENSE", null, "food", false, 0),
                CategoryRow(12, "Home-Food", "EXPENSE", parentId = 10, iconKey = "combo:rent+food", archived = false, sortOrder = 1),
            ),
            recurringRules = listOf(RecurringRuleRow(5, "EXPENSE", 179_00, 1, null, 10, "Other", "MONTHLY", 1, 20_000, null, 20_080, true)),
            records = listOf(RecordRow(7, "EXPENSE", 1_323_00, 1, null, 10, 1_795_000_000_000, "", 5, 1, 1)),
            budgets = listOf(
                BudgetRow(1, "OVERALL", null, null, 202611, 25_000_00),
                BudgetRow(2, "CATEGORY", 10, null, 202612, 0), // stop marker: not counted
            ),
            budgetOffsets = listOf(BudgetOffsetRow(1, "CATEGORY", 10, null, 7)),
        ),
        settings = BackupSettings(theme = "GRAPHITE", reminderHour = 21),
    )

    @Test fun `plain round trip keeps every row and setting`() {
        val bytes = BackupCodec.encode(file)
        assertFalse(BackupCodec.isEncrypted(bytes))
        assertEquals(file, BackupCodec.decode(bytes))
    }

    @Test fun `password round trip, wrong password and tampering are caught`() {
        val bytes = BackupCodec.encode(file, password = "secret-123")
        assertTrue(BackupCodec.isEncrypted(bytes))
        assertFalse(String(bytes).contains("SBI")) // nothing readable without the password
        assertEquals(file, BackupCodec.decode(bytes, "secret-123"))
        assertThrows(BackupError.PasswordNeeded::class.java) { BackupCodec.decode(bytes) }
        assertThrows(BackupError.WrongPassword::class.java) { BackupCodec.decode(bytes, "nope") }

        val text = String(bytes)
        val payload = Regex("\"payload\":\"([^\"]+)\"").find(text)!!.groupValues[1]
        val flipped = (if (payload[10] == 'A') 'B' else 'A').toString()
        val tampered = text.replace(payload, payload.substring(0, 10) + flipped + payload.substring(11)).toByteArray()
        assertThrows(BackupError.WrongPassword::class.java) { BackupCodec.decode(tampered, "secret-123") }
    }

    @Test fun `other files and newer versions are refused`() {
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("hello".toByteArray()) }
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("""{"format":"something-else","version":1}""".toByteArray()) }
        val newer = String(BackupCodec.encode(file)).replace("\"version\":1", "\"version\":99").toByteArray()
        assertThrows(BackupError.NewerVersion::class.java) { BackupCodec.decode(newer) }
    }

    @Test fun `unknown fields from a newer minor change are ignored`() {
        val extra = String(BackupCodec.encode(file)).replaceFirst("{", "{\"futureField\":true,").toByteArray()
        assertEquals(file, BackupCodec.decode(extra))
    }

    @Test fun `counts skip budget stop markers and categories restore parents first`() {
        val counts = file.data.counts()
        assertEquals(1, counts.records)
        assertEquals(1, counts.budgets)
        assertEquals(3, counts.categories)
        assertEquals(listOf(10L, 11L, 12L), file.data.categories.parentsFirst().map { it.id })
        assertEquals("1 record · 1 account · 1 budget · 1 recurring item · 3 categories", counts.summary)
    }

    @Test fun `rows map back to the same entities`() {
        val row = file.data.records.single()
        assertEquals(row, row.toEntity().toRow())
        val rule = file.data.recurringRules.single()
        assertEquals(rule, rule.toEntity().toRow())
        val budget = file.data.budgets.first()
        assertEquals(budget, budget.toEntity().toRow())
    }

    @Test fun `folder keeps only the newest five backups`() {
        val names = (1..8).map { "MoneyRecords-2026-11-0$it.json" } + "notes.txt"
        assertEquals(
            listOf("MoneyRecords-2026-11-03.json", "MoneyRecords-2026-11-02.json", "MoneyRecords-2026-11-01.json"),
            BackupFolder.toPrune(names),
        )
        assertEquals("MoneyRecords-2026-11-23.json", BackupFolder.fileName(java.time.LocalDate.of(2026, 11, 23)))
    }
}
