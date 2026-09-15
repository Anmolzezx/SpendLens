package com.spendlens.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.data.repository.OfflineFirstExpenseRepository
import com.spendlens.core.database.RoomTransactionRunner
import com.spendlens.core.database.SpendLensDatabase
import com.spendlens.core.database.entity.asEntity
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.sync.TestSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Repository tests against a real in-memory database.
 *
 * A fake DAO would only prove the mapping code runs; this proves the queries, the converters and the
 * mapping agree with each other, which is where the bugs actually live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineFirstExpenseRepositoryTest {
    private val fixedNow: Instant = Instant.parse("2026-09-01T09:30:00Z")
    private lateinit var database: SpendLensDatabase
    private lateinit var repository: OfflineFirstExpenseRepository
    private val syncManager = TestSyncManager()

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
            ).build()
        repository = OfflineFirstExpenseRepository(
            expenseDao = database.expenseDao(),
            transaction = RoomTransactionRunner(database),
            syncManager = syncManager,
            // Fixed, so `updatedAt` is an exact expected value rather than "roughly now".
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `round-trips an expense through the database`() =
        runTest {
            repository.upsert(expense(id = "a", merchant = "Trader Joe's", amountMinor = 4_287))

            val stored = repository.observeExpenses().first().single()

            assertEquals("Trader Joe's", stored.merchant)
            assertEquals(4_287L, stored.amountMinor)
        }

    /** Callers do not get to decide sync state — a local write is unsynced by definition. */
    @Test
    fun `upsert marks the row pending regardless of what the caller passed`() =
        runTest {
            repository.upsert(expense(id = "a", syncState = SyncState.SYNCED))

            assertEquals(
                SyncState.PENDING,
                repository
                    .observeExpenses()
                    .first()
                    .single()
                    .syncState,
            )
        }

    @Test
    fun `upsert stamps updatedAt from the clock`() =
        runTest {
            repository.upsert(expense(id = "a", updatedAt = Instant.EPOCH))

            assertEquals(
                fixedNow,
                repository
                    .observeExpenses()
                    .first()
                    .single()
                    .updatedAt,
            )
        }

    @Test
    fun `delete hides the expense from reads`() =
        runTest {
            repository.upsert(expense(id = "a"))

            repository.delete("a")

            assertTrue(repository.observeExpenses().first().isEmpty())
            assertNull(repository.observeExpense("a").first())
        }

    @Test
    fun `observeExpenses returns newest first`() =
        runTest {
            repository.upsert(expense(id = "old", occurredOn = LocalDate.parse("2026-08-01")))
            repository.upsert(expense(id = "new", occurredOn = LocalDate.parse("2026-08-20")))

            assertEquals(listOf("new", "old"), repository.observeExpenses().first().map { it.id })
        }

    @Test
    fun `observeExpensesIn returns only that month`() =
        runTest {
            repository.upsert(expense(id = "july", occurredOn = LocalDate.parse("2026-07-31")))
            repository.upsert(expense(id = "august", occurredOn = LocalDate.parse("2026-08-15")))
            repository.upsert(expense(id = "september", occurredOn = LocalDate.parse("2026-09-01")))

            val ids = repository
                .observeExpensesIn(YearMonth.of(2026, 8))
                .first()
                .map { it.id }

            assertEquals(listOf("august"), ids)
        }

    /**
     * Replaced a test asserting the opposite: while dates were instants, 20:00 UTC on 31 July was
     * July in London and August in Kolkata. With calendar dates both edges of a month are fixed, and
     * this checks the epoch-day range the repository hands the DAO is inclusive at both ends.
     */
    @Test
    fun `observeExpensesIn includes the first and last day of the month`() =
        runTest {
            repository.upsert(expense(id = "first", occurredOn = LocalDate.parse("2026-08-01")))
            repository.upsert(expense(id = "last", occurredOn = LocalDate.parse("2026-08-31")))
            repository.upsert(expense(id = "dayBefore", occurredOn = LocalDate.parse("2026-07-31")))
            repository.upsert(expense(id = "dayAfter", occurredOn = LocalDate.parse("2026-09-01")))

            val ids = repository
                .observeExpensesIn(YearMonth.of(2026, 8))
                .first()
                .map { it.id }
                .sorted()

            assertEquals(listOf("first", "last"), ids)
        }

    /** Offline-first means the save never waits for sync — it only asks for one. */
    @Test
    fun `saving and deleting each ask for a sync`() =
        runTest {
            repository.upsert(expense(id = "a"))
            assertEquals(1, syncManager.requestCount)

            repository.delete("a")
            assertEquals(2, syncManager.requestCount)
        }

    /**
     * The edit screen builds a new `Expense` from its form and knows nothing about sync. If the
     * repository trusted it, saving a synced expense would erase its version and the next upload
     * would collide with its own history.
     */
    @Test
    fun `upsert keeps the stored remote version when the caller passes none`() =
        runTest {
            val synced = expense(id = "a", syncState = SyncState.SYNCED).copy(remoteVersion = 9)
            database.expenseDao().upsert(synced.asEntity())

            repository.upsert(expense(id = "a", merchant = "Edited"))

            val stored = database.expenseDao().getExpense("a")
            assertEquals(9L, stored?.remoteVersion)
            assertEquals(SyncState.PENDING, stored?.syncState)
        }

    @Test
    fun `editing a conflicted expense keeps it in conflict`() =
        runTest {
            database.expenseDao().upsert(expense(id = "a", syncState = SyncState.CONFLICT).asEntity())

            repository.upsert(expense(id = "a", merchant = "Edited"))

            assertEquals(SyncState.CONFLICT, database.expenseDao().getExpense("a")?.syncState)
        }

    private fun expense(
        id: String,
        merchant: String = "Merchant",
        amountMinor: Long = 1_000,
        occurredOn: LocalDate = LocalDate.parse("2026-08-15"),
        syncState: SyncState = SyncState.PENDING,
        updatedAt: Instant = Instant.EPOCH,
    ) = Expense(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = occurredOn,
        categoryId = "cat-groceries",
        note = null,
        receiptImagePath = null,
        syncState = syncState,
        updatedAt = updatedAt,
        isDeleted = false,
    )
}
