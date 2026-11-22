package com.moneyrecords.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneyrecords.app.domain.model.CategoryKind

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        RecurringRuleEntity::class,
        RecordEntity::class,
        BudgetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MoneyRecordsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "moneyrecords.db"
    }
}

/** Default categories inserted once, when the database file is first created. No accounts are seeded. */
object DefaultCategories {
    data class Seed(val name: String, val kind: CategoryKind, val iconKey: String)

    val all: List<Seed> = listOf(
        Seed("Food & Dining", CategoryKind.EXPENSE, "food"),
        Seed("Groceries", CategoryKind.EXPENSE, "groceries"),
        Seed("Transport", CategoryKind.EXPENSE, "transport"),
        Seed("Fuel", CategoryKind.EXPENSE, "fuel"),
        Seed("Shopping", CategoryKind.EXPENSE, "shopping"),
        Seed("Bills & Utilities", CategoryKind.EXPENSE, "bills"),
        Seed("Rent", CategoryKind.EXPENSE, "rent"),
        Seed("Health", CategoryKind.EXPENSE, "health"),
        Seed("Education", CategoryKind.EXPENSE, "education"),
        Seed("Entertainment", CategoryKind.EXPENSE, "entertainment"),
        Seed("Travel", CategoryKind.EXPENSE, "travel"),
        Seed("Personal care", CategoryKind.EXPENSE, "personal"),
        Seed("Gifts & Donations", CategoryKind.EXPENSE, "gift"),
        Seed("Other", CategoryKind.EXPENSE, "other"),
        Seed("Salary", CategoryKind.INCOME, "salary"),
        Seed("Business", CategoryKind.INCOME, "business"),
        Seed("Interest", CategoryKind.INCOME, "interest"),
        Seed("Refunds", CategoryKind.INCOME, "refund"),
        Seed("Gifts", CategoryKind.INCOME, "gift"),
        Seed("Other", CategoryKind.INCOME, "other"),
    )
}

class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val orderByKind = mutableMapOf<CategoryKind, Int>()
        DefaultCategories.all.forEach { seed ->
            val order = orderByKind.getOrDefault(seed.kind, 0)
            orderByKind[seed.kind] = order + 1
            db.insert(
                "categories",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", seed.name)
                    put("kind", seed.kind.name)
                    put("iconKey", seed.iconKey)
                    put("archived", 0)
                    put("sortOrder", order)
                },
            )
        }
    }
}
