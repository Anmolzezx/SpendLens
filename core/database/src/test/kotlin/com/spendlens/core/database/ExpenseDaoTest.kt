package com.spendlens.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * DAO tests against a real SQLite database, in memory.
 *
 * Robolectric rather than an instrumented test so these run on the JVM in CI. A DAO test that only
 * runs on an emulator is a DAO test that does not run — and query correctness is exactly the thing
 * worth catching on every push.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseDaoTest {
    private lateinit var database: SpendLensDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
            ).build()
        dao = database.expenseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts and reads back an expense`() =
        runTest {
            dao.upsert(expense(id = "a", amountMinor = 1_234))

            val stored = dao.observeExpenses().first()

            assertEquals(1, stored.size)
            assertEquals(1_234L, stored.first().amountMinor)
        }

    /** The round trip that proves the TypeConverters work in both directions. */
    @Test
    fun `preserves Instant and SyncState across a round trip`() =
        runTest {
            val occurredAt = Instant.parse("2026-08-26T18:42:00Z")
            dao.upsert(
                expense(id = "a", occurredAt = occurredAt, syncState = SyncState.CONFLICT),
            )

            val stored = dao.observeExpenses().first().single()

            assertEquals(occurredAt, stored.occurredAt)
            assertEquals(SyncState.CONFLICT, stored.syncState)
        }

    @Test
    fun `orders by occurred_at descending`() =
        runTest {
            dao.upsert(
                listOf(
                    expense(id = "old", occurredAt = Instant.parse("2026-08-01T10:00:00Z")),
                    expense(id = "new", occurredAt = Instant.parse("2026-08-20T10:00:00Z")),
                    expense(id = "mid", occurredAt = Instant.parse("2026-08-10T10:00:00Z")),
                ),
            )

            val ids = dao.observeExpenses().first().map { it.id }

            assertEquals(listOf("new", "mid", "old"), ids)
        }

    @Test
    fun `upsert replaces an existing row rather than duplicating it`() =
        runTest {
            dao.upsert(expense(id = "a", merchant = "Old"))
            dao.upsert(expense(id = "a", merchant = "New"))

            val stored = dao.observeExpenses().first()

            assertEquals(1, stored.size)
            assertEquals("New", stored.single().merchant)
        }

    @Test
    fun `soft delete hides the row from reads but keeps it in the table`() =
        runTest {
            dao.upsert(expense(id = "a"))

            dao.softDelete(id = "a", updatedAt = 1_000L)

            assertTrue(dao.observeExpenses().first().isEmpty())
            // Still present for sync: a tombstone has to be uploaded before it can be forgotten.
            assertEquals(listOf("a"), dao.getPendingSync().map { it.id })
        }

    @Test
    fun `soft delete marks the row pending so sync picks it up`() =
        runTest {
            dao.upsert(expense(id = "a", syncState = SyncState.SYNCED))

            dao.softDelete(id = "a", updatedAt = 1_000L)

            assertEquals(SyncState.PENDING, dao.getPendingSync().single().syncState)
        }

    @Test
    fun `observeExpense returns null for a soft-deleted row`() =
        runTest {
            dao.upsert(expense(id = "a"))
            dao.softDelete(id = "a", updatedAt = 1_000L)

            assertNull(dao.observeExpense("a").first())
        }

    @Test
    fun `getPendingSync excludes synced rows`() =
        runTest {
            dao.upsert(
                listOf(
                    expense(id = "synced", syncState = SyncState.SYNCED),
                    expense(id = "pending", syncState = SyncState.PENDING),
                    expense(id = "conflict", syncState = SyncState.CONFLICT),
                ),
            )

            val ids = dao.getPendingSync().map { it.id }.sorted()

            assertEquals(listOf("conflict", "pending"), ids)
        }

    @Test
    fun `observeExpensesBetween is start-inclusive and end-exclusive`() =
        runTest {
            val start = Instant.parse("2026-08-01T00:00:00Z")
            val end = Instant.parse("2026-09-01T00:00:00Z")
            dao.upsert(
                listOf(
                    expense(id = "before", occurredAt = start.minusMillis(1)),
                    expense(id = "onStart", occurredAt = start),
                    expense(id = "inside", occurredAt = Instant.parse("2026-08-15T00:00:00Z")),
                    expense(id = "onEnd", occurredAt = end),
                ),
            )

            val ids = dao
                .observeExpensesBetween(start.toEpochMilli(), end.toEpochMilli())
                .first()
                .map { it.id }
                .sorted()

            assertEquals(listOf("inside", "onStart"), ids)
        }

    @Test
    fun `purgeTombstones removes only synced tombstones older than the cutoff`() =
        runTest {
            dao.upsert(
                listOf(
                    expense(id = "liveRow", syncState = SyncState.SYNCED),
                    expense(id = "oldTombstone", syncState = SyncState.SYNCED, isDeleted = true, updatedAt = 100L),
                    expense(id = "newTombstone", syncState = SyncState.SYNCED, isDeleted = true, updatedAt = 900L),
                    expense(
                        id = "unsyncedTombstone",
                        syncState = SyncState.PENDING,
                        isDeleted = true,
                        updatedAt = 100L,
                    ),
                ),
            )

            val purged = dao.purgeTombstones(olderThan = 500L)

            assertEquals(1, purged)
            // The unsynced tombstone survives — deleting it would lose the deletion itself.
            assertEquals(listOf("unsyncedTombstone"), dao.getPendingSync().map { it.id })
        }

    private fun expense(
        id: String,
        merchant: String = "Merchant",
        amountMinor: Long = 1_000,
        occurredAt: Instant = Instant.parse("2026-08-15T12:00:00Z"),
        syncState: SyncState = SyncState.SYNCED,
        isDeleted: Boolean = false,
        updatedAt: Long = 0L,
    ) = ExpenseEntity(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = "USD",
        occurredAt = occurredAt,
        categoryId = "cat-groceries",
        note = null,
        receiptImagePath = null,
        syncState = syncState,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        isDeleted = isDeleted,
    )
}
