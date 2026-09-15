package com.spendlens.feature.expenses.conflict

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.model.Category
import com.spendlens.core.model.DefaultCategories
import com.spendlens.core.model.Expense
import com.spendlens.core.model.ExpenseConflict
import com.spendlens.core.model.ExpenseField
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.repository.TestExpenseConflictRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Robolectric only because the ViewModel reads its typed route with `SavedStateHandle.toRoute()`, which
 * builds an Android `Bundle` under the hood. Nothing else here touches the framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpenseConflictViewModelTest {
    private val local = Expense(
        id = "a",
        merchant = "shop",
        amountMinor = 440_000,
        currency = "USD",
        occurredOn = LocalDate.parse("2026-09-15"),
        categoryId = DefaultCategories.groceries.id,
        note = null,
        receiptImagePath = null,
        syncState = SyncState.CONFLICT,
        updatedAt = Instant.parse("2026-09-15T12:58:00Z"),
        isDeleted = false,
    )
    private val remote = local.copy(
        amountMinor = 500_000,
        note = "Corrected on the tablet",
        updatedAt = Instant.parse("2026-09-15T13:00:00Z"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows both versions and what differs between them`() =
        runTest {
            val viewModel = viewModel(TestExpenseConflictRepository(listOf(ExpenseConflict(local, remote))))

            viewModel.uiState.test {
                assertEquals(ExpenseConflictUiState.Loading, awaitItem())
                val conflict = (awaitItem() as ExpenseConflictUiState.Deciding).conflict

                assertEquals("$4,400.00", conflict.thisDevice.formattedAmount)
                assertEquals("$5,000.00", conflict.otherDevice.formattedAmount)
                assertEquals("Corrected on the tablet", conflict.otherDevice.note)
                assertEquals("Groceries", conflict.thisDevice.categoryName)
                assertEquals(setOf(ExpenseField.AMOUNT, ExpenseField.NOTE), conflict.differences)
                // Formatted in the injected zone, so the edit time reads the same on any machine.
                assertTrue(
                    conflict.otherDevice.formattedEditedAt,
                    conflict.otherDevice.formattedEditedAt.contains("1:00"),
                )
            }
        }

    @Test
    fun `keeping this device's version records it and closes`() =
        runTest {
            val repository = TestExpenseConflictRepository(listOf(ExpenseConflict(local, remote)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitDeciding()

                viewModel.keepThisDevice()

                awaitResolved()
            }
            assertEquals(listOf("a" to true), repository.decisions)
        }

    @Test
    fun `keeping the other device's version records it and closes`() =
        runTest {
            val repository = TestExpenseConflictRepository(listOf(ExpenseConflict(local, remote)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitDeciding()

                viewModel.keepOtherDevice()

                awaitResolved()
            }
            assertEquals(listOf("a" to false), repository.decisions)
        }

    /** Two quick taps on different buttons must not keep one version and then overwrite it with the other. */
    @Test
    fun `only the first choice counts`() =
        runTest {
            val repository = TestExpenseConflictRepository(listOf(ExpenseConflict(local, remote)))
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitDeciding()

                viewModel.keepThisDevice()
                viewModel.keepOtherDevice()

                awaitResolved()
            }
            assertEquals(listOf("a" to true), repository.decisions)
        }

    /** A stale "Review" tap, or a conflict a sync settled while the screen was opening. */
    @Test
    fun `a conflict that no longer exists resolves straight away`() =
        runTest {
            val viewModel = viewModel(TestExpenseConflictRepository())

            viewModel.uiState.test {
                assertEquals(ExpenseConflictUiState.Loading, awaitItem())
                assertEquals(ExpenseConflictUiState.Resolved, awaitItem())
            }
        }

    @Test
    fun `a deletion on this device is shown as a deletion`() =
        runTest {
            val deletedHere = ExpenseConflict(local.copy(isDeleted = true), remote)
            val viewModel = viewModel(TestExpenseConflictRepository(listOf(deletedHere)))

            viewModel.uiState.test {
                val conflict = awaitDeciding().conflict

                assertTrue(conflict.thisDevice.isDeleted)
                assertEquals(setOf(ExpenseField.DELETED), conflict.differences)
            }
        }

    private suspend fun ReceiveTurbine<ExpenseConflictUiState>.awaitDeciding(): ExpenseConflictUiState.Deciding {
        while (true) {
            val state = awaitItem()
            if (state is ExpenseConflictUiState.Deciding) return state
        }
    }

    private suspend fun ReceiveTurbine<ExpenseConflictUiState>.awaitResolved() {
        while (awaitItem() != ExpenseConflictUiState.Resolved) Unit
    }

    private fun viewModel(repository: TestExpenseConflictRepository) =
        ExpenseConflictViewModel(
            // The keys toRoute reads are the route's property names.
            savedStateHandle = SavedStateHandle(mapOf("expenseId" to "a")),
            conflictRepository = repository,
            categoryRepository = FakeCategoryRepository(DefaultCategories.all),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        )

    private class FakeCategoryRepository(
        private val categories: List<Category>,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = flowOf(categories)
    }
}
