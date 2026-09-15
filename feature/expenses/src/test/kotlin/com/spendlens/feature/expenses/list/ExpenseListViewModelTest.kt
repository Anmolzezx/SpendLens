package com.spendlens.feature.expenses.list

import app.cash.turbine.test
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.model.Category
import com.spendlens.core.model.DefaultCategories
import com.spendlens.core.model.Expense
import com.spendlens.core.model.ExpenseConflict
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.repository.TestExpenseConflictRepository
import com.spendlens.core.testing.repository.TestExpenseRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * ViewModel tests with no Android framework at all — the payoff for keeping formatting in pure
 * functions and injecting `Clock`, `ZoneId` and `Locale` instead of reading them statically.
 *
 * Turbine rather than `.first()`: a `StateFlow` that emits Loading then Success is a *sequence*, and
 * asserting on the sequence is what catches a ViewModel that skips its loading state or emits twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModelTest {
    private val fixedNow: Instant = Instant.parse("2026-08-26T12:00:00Z")
    private val utc: ZoneId = ZoneOffset.UTC

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts in Loading`() =
        runTest {
            val viewModel = viewModel(TestExpenseRepository())

            assertEquals(ExpenseListUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `emits Empty when there are no expenses`() =
        runTest {
            val viewModel = viewModel(TestExpenseRepository())

            viewModel.uiState.test {
                assertEquals(ExpenseListUiState.Loading, awaitItem())
                assertEquals(ExpenseListUiState.Empty(hasActiveFilters = false), awaitItem())
            }
        }

    @Test
    fun `maps expenses to formatted rows`() =
        runTest {
            val repository = TestExpenseRepository(listOf(expense(id = "a", amountMinor = 4_287)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem() // Loading
                val state = awaitItem() as ExpenseListUiState.Success

                assertEquals(1, state.expenses.size)
                assertEquals("$42.87", state.expenses.single().formattedAmount)
                assertEquals("Groceries", state.expenses.single().categoryName)
            }
        }

    /** The reason the repository returns a Flow: a change downstream re-renders with no re-fetch. */
    @Test
    fun `re-emits when the repository changes`() =
        runTest {
            val repository = TestExpenseRepository(listOf(expense(id = "a")))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem() // Loading
                assertEquals(1, (awaitItem() as ExpenseListUiState.Success).expenses.size)

                repository.emit(listOf(expense(id = "a"), expense(id = "b")))

                assertEquals(2, (awaitItem() as ExpenseListUiState.Success).expenses.size)
            }
        }

    /** A conflict needs the user, not the network, so it is not "not synced" — it has its own notice. */
    @Test
    fun `counts pending uploads and conflicts separately`() =
        runTest {
            val conflicted = expense(id = "c", syncState = SyncState.CONFLICT)
            val repository = TestExpenseRepository(
                listOf(
                    expense(id = "a", syncState = SyncState.SYNCED),
                    expense(id = "b", syncState = SyncState.PENDING),
                    conflicted,
                ),
            )
            val conflicts =
                TestExpenseConflictRepository(listOf(ExpenseConflict(conflicted, conflicted.copy(amountMinor = 1))))
            val viewModel = viewModel(repository, conflicts)

            viewModel.uiState.test {
                awaitItem()
                val state = awaitItem() as ExpenseListUiState.Success
                assertEquals(1, state.pendingCount)
                assertEquals(listOf("c"), state.conflictedExpenseIds)
            }
        }

    /** Deleted here, edited elsewhere: no row to tap, so the notice is the only way to reach it. */
    @Test
    fun `offers conflicts even when no expense is visible`() =
        runTest {
            val deleted = expense(id = "gone", syncState = SyncState.CONFLICT).copy(isDeleted = true)
            val conflicts =
                TestExpenseConflictRepository(listOf(ExpenseConflict(deleted, deleted.copy(isDeleted = false))))
            val viewModel = viewModel(TestExpenseRepository(listOf(deleted)), conflicts)

            viewModel.uiState.test {
                awaitItem()
                assertEquals(
                    ExpenseListUiState.Empty(hasActiveFilters = false, conflictedExpenseIds = persistentListOf("gone")),
                    awaitItem(),
                )
            }
        }

    /** Only the current month counts toward the header total, even though the list shows everything. */
    @Test
    fun `month total excludes other months`() =
        runTest {
            val repository = TestExpenseRepository(
                listOf(
                    expense(id = "thisMonth", amountMinor = 1_000, occurredOn = LocalDate.parse("2026-08-10")),
                    expense(id = "lastMonth", amountMinor = 9_999, occurredOn = LocalDate.parse("2026-07-10")),
                ),
            )
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                val state = awaitItem() as ExpenseListUiState.Success

                assertEquals("$10.00", state.monthTotal)
                assertEquals("both rows still listed", 2, state.expenses.size)
            }
        }

    @Test
    fun `deleting an expense removes it from the state`() =
        runTest {
            val repository = TestExpenseRepository(listOf(expense(id = "a"), expense(id = "b")))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                assertEquals(2, (awaitItem() as ExpenseListUiState.Success).expenses.size)

                viewModel.deleteExpense("a")

                assertEquals(listOf("b"), (awaitItem() as ExpenseListUiState.Success).expenses.map { it.id })
            }
            assertTrue("tombstone kept for sync", repository.current.single { it.id == "a" }.isDeleted)
        }

    private fun viewModel(
        repository: TestExpenseRepository,
        conflicts: TestExpenseConflictRepository = TestExpenseConflictRepository(),
    ) = ExpenseListViewModel(
        expenseRepository = repository,
        categoryRepository = FakeCategoryRepository(DefaultCategories.all),
        conflictRepository = conflicts,
        clock = Clock.fixed(fixedNow, utc),
        zoneId = utc,
        locale = Locale.US,
    )

    private fun expense(
        id: String,
        amountMinor: Long = 1_000,
        occurredOn: LocalDate = LocalDate.parse("2026-08-15"),
        syncState: SyncState = SyncState.SYNCED,
    ) = Expense(
        id = id,
        merchant = "Merchant $id",
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = occurredOn,
        categoryId = DefaultCategories.groceries.id,
        note = null,
        receiptImagePath = null,
        syncState = syncState,
        updatedAt = Instant.EPOCH,
        isDeleted = false,
    )

    private class FakeCategoryRepository(
        private val categories: List<Category>,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = flowOf(categories)
    }
}
