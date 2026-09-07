package com.spendlens.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.data.repository.OfflineFirstExpenseRepository
import com.spendlens.core.database.SpendLensDatabase
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
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
import java.time.YearMonth
import java.time.ZoneId
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

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
            ).build()
        repository = OfflineFirstExpenseRepository(
            expenseDao = database.expenseDao(),
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
            repository.upsert(expense(id = "old", occurredAt = Instant.parse("2026-08-01T10:00:00Z")))
            repository.upsert(expense(id = "new", occurredAt = Instant.parse("2026-08-20T10:00:00Z")))

            assertEquals(listOf("new", "old"), repository.observeExpenses().first().map { it.id })
        }

    @Test
    fun `observeExpensesIn returns only that month`() =
        runTest {
            repository.upsert(expense(id = "july", occurredAt = Instant.parse("2026-07-31T10:00:00Z")))
            repository.upsert(expense(id = "august", occurredAt = Instant.parse("2026-08-15T10:00:00Z")))
            repository.upsert(expense(id = "september", occurredAt = Instant.parse("2026-09-01T10:00:00Z")))

            val ids = repository
                .observeExpensesIn(YearMonth.of(2026, 8), ZoneId.of("UTC"))
                .first()
                .map { it.id }

            assertEquals(listOf("august"), ids)
        }

    /**
     * The month boundary moves with the zone. Recorded at 20:00 UTC on 31 July, this expense is
     * still July in London and already August in Kolkata (UTC+5:30).
     */
    @Test
    fun `observeExpensesIn respects the supplied timezone`() =
        runTest {
            repository.upsert(expense(id = "boundary", occurredAt = Instant.parse("2026-07-31T20:00:00Z")))

            val inUtc = repository.observeExpensesIn(YearMonth.of(2026, 8), ZoneId.of("UTC")).first()
            val inKolkata = repository.observeExpensesIn(YearMonth.of(2026, 8), ZoneId.of("Asia/Kolkata")).first()

            assertTrue("still July in UTC", inUtc.isEmpty())
            assertEquals(listOf("boundary"), inKolkata.map { it.id })
        }

    private fun expense(
        id: String,
        merchant: String = "Merchant",
        amountMinor: Long = 1_000,
        occurredAt: Instant = Instant.parse("2026-08-15T12:00:00Z"),
        syncState: SyncState = SyncState.PENDING,
        updatedAt: Instant = Instant.EPOCH,
    ) = Expense(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = "USD",
        occurredAt = occurredAt,
        categoryId = "cat-groceries",
        note = null,
        receiptImagePath = null,
        syncState = syncState,
        updatedAt = updatedAt,
        isDeleted = false,
    )
}
