package com.spendlens.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.database.entity.BudgetEntity
import com.spendlens.core.database.entity.ExpenseConflictEntity
import com.spendlens.core.database.entity.SyncCursorEntity
import com.spendlens.core.database.migration.addSpendLensMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Proves every migration runs against a real on-disk database, from each shipped version.
 *
 * Old databases are built from the **committed schema JSON** (`schemas/…/N.json`) — its `createSql`,
 * indices and Room's identity hash — rather than from DDL copied into this file. A hand-copied
 * fixture can drift from what actually shipped; the exported schema is what shipped.
 *
 * The database is opened with [addSpendLensMigrations], the same function production uses, so a
 * migration missing from the app is also missing here and the test fails instead of the upgrade.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val databaseFiles = mutableListOf<File>()
    private val openDatabases = mutableListOf<SpendLensDatabase>()
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        databaseFile = newDatabaseFile()
    }

    /**
     * One teardown, in order: close every connection, then delete the files. Two separate `@After`
     * methods would run in an unspecified order and could delete a database that is still open.
     */
    @After
    fun tearDown() {
        openDatabases.forEach { it.close() }
        databaseFiles.forEach { file ->
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }
    }

    // -- 1 → 3 --------------------------------------------------------------------------------------

    @Test
    fun `upgrading from version 1 preserves expenses`() =
        runTest {
            createDatabaseAt(version = 1) { insertExpense(occurredAt = 1_000) }

            val expense = openWithRoom(UTC)
                .expenseDao()
                .observeExpenses()
                .first()
                .single()

            assertEquals("Trader Joe's", expense.merchant)
            assertEquals(4_287L, expense.amountMinor)
        }

    @Test
    fun `upgrading from version 1 preserves categories`() =
        runTest {
            createDatabaseAt(version = 1) {
                execSQL(
                    "INSERT INTO categories (id, name, color_index, icon_key) " +
                        "VALUES ('cat-groceries', 'Groceries', 0, 'cart')",
                )
            }

            assertEquals("Groceries", openWithRoom(UTC).categoryDao().getCategory("cat-groceries")?.name)
        }

    @Test
    fun `upgrading from version 1 adds a usable budgets table`() =
        runTest {
            createDatabaseAt(version = 1)
            val database = openWithRoom(UTC)

            database.budgetDao().upsert(
                BudgetEntity(
                    categoryId = "cat-groceries",
                    month = YearMonth.of(2026, 9),
                    limitMinor = 30_000,
                    currency = "USD",
                ),
            )

            assertEquals(
                30_000L,
                database
                    .budgetDao()
                    .observeBudgets("2026-09")
                    .first()
                    .single()
                    .limitMinor,
            )
        }

    // -- 2 → 3 --------------------------------------------------------------------------------------

    /**
     * The point of migration 3. 23:00 UTC on 31 August is still the 31st in London but already
     * 1 September in Kolkata, and the stored date must be the one the user saw on their own device.
     */
    @Test
    fun `migration 3 converts an instant to the calendar date in the device zone`() =
        runTest {
            val lateOn31stUtc = Instant.parse("2026-08-31T23:00:00Z").toEpochMilli()

            // Separate files: reusing one path while the first connection is still open is how a test
            // ends up waiting on a SQLite lock instead of failing.
            createDatabaseAt(version = 2) { insertExpense(occurredAt = lateOn31stUtc) }
            val inKolkata = openWithRoom(ZoneId.of("Asia/Kolkata"))
                .expenseDao()
                .observeExpenses()
                .first()
                .single()
            assertEquals(LocalDate.of(2026, 9, 1), inKolkata.occurredOn)

            databaseFile = newDatabaseFile()
            createDatabaseAt(version = 2) { insertExpense(occurredAt = lateOn31stUtc) }
            val inUtc = openWithRoom(UTC)
                .expenseDao()
                .observeExpenses()
                .first()
                .single()
            assertEquals(LocalDate.of(2026, 8, 31), inUtc.occurredOn)
        }

    @Test
    fun `migration 3 adds remote_version as null on existing rows`() =
        runTest {
            createDatabaseAt(version = 2) { insertExpense(occurredAt = 1_000) }

            assertNull(
                openWithRoom(UTC)
                    .expenseDao()
                    .observeExpenses()
                    .first()
                    .single()
                    .remoteVersion,
            )
        }

    // -- 3 → 4 --------------------------------------------------------------------------------------

    @Test
    fun `upgrading from version 3 keeps expenses and adds usable sync tables`() =
        runTest {
            createDatabaseAt(version = 3) {
                insertExpense(occurredAt = LocalDate.of(2026, 9, 15).toEpochDay())
            }
            val database = openWithRoom(UTC)

            database.syncCursorDao().upsert(SyncCursorEntity(stream = "expenses", cursor = 41))
            database.expenseConflictDao().upsert(
                ExpenseConflictEntity(
                    expenseId = "a",
                    merchant = "Trader Joe's",
                    amountMinor = 5_000,
                    currency = "USD",
                    occurredOn = LocalDate.of(2026, 9, 15),
                    categoryId = "cat-groceries",
                    note = null,
                    updatedAt = Instant.EPOCH,
                    isDeleted = false,
                    serverVersion = 41,
                ),
            )

            assertEquals(LocalDate.of(2026, 9, 15), database.expenseDao().getExpense("a")?.occurredOn)
            assertEquals(41L, database.syncCursorDao().getSyncCursor("expenses")?.cursor)
            assertEquals(5_000L, database.expenseConflictDao().getConflict("a")?.amountMinor)
        }

    // -- 4 → 5 --------------------------------------------------------------------------------------

    /** A device that synced before this version keeps its cursor, and simply has no sync time yet. */
    @Test
    fun `upgrading from version 4 keeps the cursor and adds an empty last-synced time`() =
        runTest {
            createDatabaseAt(version = 4) {
                execSQL("INSERT INTO sync_cursors (stream, cursor) VALUES ('expenses', 41)")
            }

            val cursor = openWithRoom(UTC).syncCursorDao().getSyncCursor("expenses")

            assertEquals(41L, cursor?.cursor)
            assertNull(cursor?.lastSyncedAt)
        }

    // -- helpers ------------------------------------------------------------------------------------

    private fun newDatabaseFile(): File =
        File.createTempFile("migration-test", ".db").apply { delete() }.also { databaseFiles += it }

    /** Builds the database exactly as schema [version] left it, then lets [seed] add rows. */
    private fun createDatabaseAt(
        version: Int,
        seed: SQLiteDatabase.() -> Unit = {},
    ) {
        val schema = JSONObject(File("$SCHEMA_DIR/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            // Room's schema fingerprint; without it the open treats this as a brand-new database and
            // no migration runs at all.
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.seed()
            db.version = version
        }
    }

    /**
     * A row in the columns every version from 1 has. Before v3 `occurred_at` holds epoch milliseconds;
     * a v3 caller passes an epoch day. `remote_version` is left to its default.
     */
    private fun SQLiteDatabase.insertExpense(occurredAt: Long) {
        execSQL(
            """
            INSERT INTO expenses
            (id, merchant, amount_minor, currency, occurred_at, category_id, note,
             receipt_image_path, sync_state, updated_at, is_deleted)
            VALUES ('a', 'Trader Joe''s', 4287, 'USD', $occurredAt, 'cat-groceries', NULL,
                    NULL, 'SYNCED', 1000, 0)
            """.trimIndent(),
        )
    }

    private fun openWithRoom(zoneId: ZoneId): SpendLensDatabase =
        Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
                databaseFile.absolutePath,
            ).addSpendLensMigrations(zoneId)
            .build()
            .also { openDatabases += it }

    private companion object {
        const val SCHEMA_DIR = "schemas/com.spendlens.core.database.SpendLensDatabase"
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
