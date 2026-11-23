package com.moneyrecords.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneyrecords.app.domain.model.CategoryKind

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        RecurringRuleEntity::class,
        RecordEntity::class,
        BudgetEntity::class,
        BudgetOffsetEntity::class,
        DetectedEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MoneyRecordsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetOffsetDao(): BudgetOffsetDao
    abstract fun backupDao(): BackupDao
    abstract fun detectedDao(): DetectedDao

    companion object {
        const val NAME = "moneyrecords.db"
    }
}

/** v2: budgets gain a scope (OVERALL / CATEGORY / REST). Existing rows keep their meaning. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN scope TEXT NOT NULL DEFAULT 'CATEGORY'")
        db.execSQL("UPDATE budgets SET scope = 'OVERALL' WHERE categoryId IS NULL")
    }
}

/**
 * v3: account budgets and hand-picked offsets.
 * - budgets gains `accountId` (FK to accounts). SQLite can't add a FK column in place, so the table
 *   is rebuilt (statements copied from schemas/3.json) and existing rows are copied across.
 * - new table budget_offsets.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`scope` TEXT NOT NULL DEFAULT 'CATEGORY', `categoryId` INTEGER, `accountId` INTEGER, " +
                "`yearMonth` INTEGER NOT NULL, `limitAmount` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `budgets_new` (`id`, `scope`, `categoryId`, `accountId`, `yearMonth`, `limitAmount`) " +
                "SELECT `id`, `scope`, `categoryId`, NULL, `yearMonth`, `limitAmount` FROM `budgets`",
        )
        db.execSQL("DROP TABLE `budgets`")
        db.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_yearMonth` ON `budgets` (`categoryId`, `yearMonth`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_accountId` ON `budgets` (`accountId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_offsets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`scope` TEXT NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `recordId` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recordId`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_offsets_recordId` ON `budget_offsets` (`recordId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_budget_offsets_scope_categoryId_accountId` ON `budget_offsets` (`scope`, `categoryId`, `accountId`)",
        )
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

/**
 * v4: split payments. Accounts gain `system` (the hidden "To collect" account); records gain the
 * split columns. All are plain added columns with defaults, so existing rows are untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `system` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `records` ADD COLUMN `splitOf` INTEGER")
        db.execSQL("ALTER TABLE `records` ADD COLUMN `person` TEXT")
        db.execSQL("ALTER TABLE `records` ADD COLUMN `awaitingMove` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `records` ADD COLUMN `splitTotal` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_records_splitOf` ON `records` (`splitOf`)")
    }
}

/**
 * v5: catching payments. Accounts gain `digits` (to match bank SMS); new table `detected` holds
 * alerts waiting to be added or ignored. Statements copied from schemas/5.json.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `digits` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `detected` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source` TEXT NOT NULL, " +
                "`origin` TEXT NOT NULL, `direction` TEXT NOT NULL, `amount` INTEGER NOT NULL, `accountId` INTEGER, `toAccountId` INTEGER, " +
                "`accountDigits` TEXT, `counterparty` TEXT, `ref` TEXT, `upiLite` INTEGER NOT NULL DEFAULT 0, " +
                "`occurredAt` INTEGER NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, `recordId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_ref` ON `detected` (`ref`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_occurredAt` ON `detected` (`occurredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_status` ON `detected` (`status`)")
    }
}

/** v6: UPI Lite as its own account type — which app it's in and which bank funds it. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `liteApp` TEXT")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `fundingAccountId` INTEGER")
    }
}
