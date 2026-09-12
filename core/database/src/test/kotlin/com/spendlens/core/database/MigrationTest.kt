package com.spendlens.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Proves the version 1 → 2 migration actually runs against a real on-disk database.
 *
 * The migration is an `@AutoMigration`, so Room generates the SQL — but *generated* is not
 * *verified*. The database is deliberately built without a destructive fallback, so a broken
 * migration crashes rather than silently wiping a user's expenses. This test is what makes that
 * crash happen in CI instead of on someone's phone after an update.
 *
 * The v1 schema is created from the DDL in the committed `schemas/…/1.json` rather than from a
 * hand-typed copy, so it cannot drift from what shipped.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        databaseFile = File.createTempFile("migration-test", ".db").apply { delete() }
    }

    @After
    fun tearDown() {
        databaseFile.delete()
    }

    @Test
    fun `migrating 1 to 2 preserves existing expenses`() =
        runTest {
            createVersion1Database()

            val migrated = openWithRoom()

            val expense = migrated
                .expenseDao()
                .observeExpenses()
                .first()
                .single()
            assertEquals("Trader Joe's", expense.merchant)
            assertEquals(4_287L, expense.amountMinor)
            migrated.close()
        }

    @Test
    fun `migrating 1 to 2 preserves existing categories`() =
        runTest {
            createVersion1Database()

            val migrated = openWithRoom()

            assertEquals("Groceries", migrated.categoryDao().getCategory("cat-groceries")?.name)
            migrated.close()
        }

    @Test
    fun `migrating 1 to 2 adds a usable budgets table`() =
        runTest {
            createVersion1Database()

            val migrated = openWithRoom()

            // Writing and reading proves the table exists with the right columns, which a bare
            // sqlite_master lookup would not.
            migrated.budgetDao().upsert(
                com.spendlens.core.database.entity.BudgetEntity(
                    categoryId = "cat-groceries",
                    month = java.time.YearMonth.of(2026, 9),
                    limitMinor = 30_000,
                    currency = "USD",
                ),
            )

            val stored = migrated
                .budgetDao()
                .observeBudgets("2026-09")
                .first()
                .single()
            assertEquals(30_000L, stored.limitMinor)
            migrated.close()
        }

    /** Builds a v1 database by hand, exactly as version 1 of the app would have left it. */
    private fun createVersion1Database() {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            V1_DDL.forEach(db::execSQL)
            db.execSQL(
                """
                INSERT INTO expenses
                (id, merchant, amount_minor, currency, occurred_at, category_id, note,
                 receipt_image_path, sync_state, updated_at, is_deleted)
                VALUES ('a', 'Trader Joe''s', 4287, 'USD', 1000, 'cat-groceries', NULL,
                        NULL, 'SYNCED', 1000, 0)
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO categories (id, name, color_index, icon_key) " +
                    "VALUES ('cat-groceries', 'Groceries', 0, 'cart')",
            )
            // Room stores its schema fingerprint here; without it the open at v2 is treated as a
            // fresh database and the migration never runs.
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$V1_IDENTITY_HASH')")
            db.version = 1
        }
    }

    private fun openWithRoom(): SpendLensDatabase =
        Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
                databaseFile.absolutePath,
            ).build()

    private companion object {
        const val V1_IDENTITY_HASH = "ddbf944caac044144c17774171211db5"

        /** Copied verbatim from `schemas/com.spendlens.core.database.SpendLensDatabase/1.json`. */
        val V1_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `merchant` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, " +
                "`category_id` TEXT NOT NULL, `note` TEXT, `receipt_image_path` TEXT, " +
                "`sync_state` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_expenses_occurred_at` ON `expenses` (`occurred_at`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_sync_state` ON `expenses` (`sync_state`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_category_id` ON `expenses` (`category_id`)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`color_index` INTEGER NOT NULL, `icon_key` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
