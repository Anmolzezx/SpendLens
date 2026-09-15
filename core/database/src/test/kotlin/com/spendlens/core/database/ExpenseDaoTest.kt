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
import java.time.LocalDate

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
    fun `preserves LocalDate and SyncState across a round trip`() =
        runTest {
            val occurredOn = LocalDate.parse("2026-08-26")
            dao.upsert(
                expense(id = "a", occurredOn = occurredOn, syncState = SyncState.CONFLICT),
            )

            val stored = dao.observeExpenses().first().single()

            assertEquals(occurredOn, stored.occurredOn)
            assertEquals(SyncState.CONFLICT, stored.syncState)
        }

    @Test
    fun `orders by occurred_on descending`() =
        runTest {
            dao.upsert(
                listOf(
                    expense(id = "old", occurredOn = LocalDate.parse("2026-08-01")),
                    expense(id = "new", occurredOn = LocalDate.parse("2026-08-20")),
                    expense(id = "mid", occurredOn = LocalDate.parse("2026-08-10")),
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

    /** A conflicted row is not re-uploaded: the server would refuse it again until the user decides. */
    @Test
    fun `getPendingSync returns only pending rows, not synced or conflicted ones`() =
        runTest {
            dao.upsert(
                listOf(
                    expense(id = "synced", syncState = SyncState.SYNCED),
                    expense(id = "pending", syncState = SyncState.PENDING),
                    expense(id = "conflict", syncState = SyncState.CONFLICT),
                ),
            )

            assertEquals(listOf("pending"), dao.getPendingSync().map { it.id })
        }

    @Test
    fun `soft delete leaves a conflicted row in conflict`() =
        runTest {
            dao.upsert(expense(id = "a", syncState = SyncState.CONFLICT))

            dao.softDelete(id = "a", updatedAt = 1_000L)

            val stored = dao.getExpense("a")
            assertEquals(true, stored?.isDeleted)
            assertEquals(SyncState.CONFLICT, stored?.syncState)
        }

    @Test
    fun `getExpense returns tombstones`() =
        runTest {
            dao.upsert(expense(id = "a", isDeleted = true))

            assertEquals("a", dao.getExpense("a")?.id)
        }

    @Test
    fun `markPushed marks an unchanged row synced at the server version`() =
        runTest {
            dao.upsert(expense(id = "a", syncState = SyncState.PENDING, updatedAt = 1_000L))

            dao.markPushed(id = "a", version = 7, pushedUpdatedAt = Instant.ofEpochMilli(1_000L))

            val stored = dao.getExpense("a")
            assertEquals(SyncState.SYNCED, stored?.syncState)
            assertEquals(7L, stored?.remoteVersion)
        }

    /** The user saved again while the upload was in flight; that newer edit still has to go up. */
    @Test
    fun `markPushed keeps a row edited since it was read pending, but records the version`() =
        runTest {
            dao.upsert(expense(id = "a", syncState = SyncState.PENDING, updatedAt = 2_000L))

            dao.markPushed(id = "a", version = 7, pushedUpdatedAt = Instant.ofEpochMilli(1_000L))

            val stored = dao.getExpense("a")
            assertEquals(SyncState.PENDING, stored?.syncState)
            assertEquals(7L, stored?.remoteVersion)
        }

    @Test
    fun `observeExpensesBetween is start-inclusive and end-exclusive`() =
        runTest {
            val start = LocalDate.parse("2026-08-01")
            val end = LocalDate.parse("2026-09-01")
            dao.upsert(
                listOf(
                    expense(id = "before", occurredOn = start.minusDays(1)),
                    expense(id = "onStart", occurredOn = start),
                    expense(id = "inside", occurredOn = LocalDate.parse("2026-08-15")),
                    expense(id = "onEnd", occurredOn = end),
                ),
            )

            val ids = dao
                .observeExpensesBetween(start.toEpochDay(), end.toEpochDay())
                .first()
                .map { it.id }
                .sorted()

            assertEquals(listOf("inside", "onStart"), ids)
        }

    /** Several expenses share a calendar day; the most recently edited one comes first. */
    @Test
    fun `breaks same-day ties by most recently updated`() =
        runTest {
            val sameDay = LocalDate.parse("2026-08-15")
            dao.upsert(
                listOf(
                    expense(id = "older", occurredOn = sameDay, updatedAt = 1_000L),
                    expense(id = "newer", occurredOn = sameDay, updatedAt = 9_000L),
                    expense(id = "middle", occurredOn = sameDay, updatedAt = 5_000L),
                ),
            )

            assertEquals(listOf("newer", "middle", "older"), dao.observeExpenses().first().map { it.id })
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
        occurredOn: LocalDate = LocalDate.parse("2026-08-15"),
        syncState: SyncState = SyncState.SYNCED,
        isDeleted: Boolean = false,
        updatedAt: Long = 0L,
    ) = ExpenseEntity(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = occurredOn,
        categoryId = "cat-groceries",
        note = null,
        receiptImagePath = null,
        syncState = syncState,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        isDeleted = isDeleted,
    )
}
