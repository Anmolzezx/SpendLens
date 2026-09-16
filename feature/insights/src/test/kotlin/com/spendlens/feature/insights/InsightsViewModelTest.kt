package com.spendlens.feature.insights

import app.cash.turbine.test
import com.spendlens.core.data.repository.BudgetRepository
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.model.Budget
import com.spendlens.core.model.Category
import com.spendlens.core.model.DefaultCategories
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.repository.TestExpenseRepository
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
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

/** "Now" is 15 September 2026, so the trend window runs April to September. */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val fixedNow: Instant = Instant.parse("2026-09-15T12:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the trend covers six months, oldest first, ending with this one`() =
        runTest {
            val viewModel = viewModel(TestExpenseRepository(listOf(expense(on = "2026-09-02"))))

            viewModel.uiState.test {
                awaitItem()
                val trend = (awaitItem() as InsightsUiState.Success).trend

                assertEquals(listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep"), trend.map { it.label })
                assertEquals(listOf(false, false, false, false, false, true), trend.map { it.isCurrentMonth })
            }
        }

    @Test
    fun `bars are sized against the biggest month`() =
        runTest {
            val repository = TestExpenseRepository(
                listOf(
                    expense(on = "2026-08-02", amountMinor = 10_000),
                    expense(on = "2026-09-02", amountMinor = 2_500),
                ),
            )
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                val trend = (awaitItem() as InsightsUiState.Success).trend.associateBy { it.label }

                assertEquals(1f, trend.getValue("Aug").fraction, 0.001f)
                assertEquals(0.25f, trend.getValue("Sep").fraction, 0.001f)
                assertEquals(0f, trend.getValue("Jul").fraction, 0.001f)
                assertEquals("$100.00", trend.getValue("Aug").amount)
            }
        }

    /** A quiet month used to hide six months of history behind the empty state. */
    @Test
    fun `a month with no spending still shows the months that had some`() =
        runTest {
            val viewModel = viewModel(TestExpenseRepository(listOf(expense(on = "2026-07-02", amountMinor = 5_000))))

            viewModel.uiState.test {
                awaitItem()
                val state = awaitItem() as InsightsUiState.Success

                assertEquals("$0.00", state.totalSpend)
                assertTrue("no spending this month, so no categories", state.categories.isEmpty())
                assertEquals(1f, state.trend.single { it.label == "Jul" }.fraction, 0.001f)
            }
        }

    @Test
    fun `nothing in the whole window is empty`() =
        runTest {
            val viewModel = viewModel(TestExpenseRepository(listOf(expense(on = "2025-01-02"))))

            viewModel.uiState.test {
                awaitItem()
                assertEquals(InsightsUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `this month's categories come from this month only`() =
        runTest {
            val repository = TestExpenseRepository(
                listOf(
                    expense(on = "2026-08-02", amountMinor = 9_999, categoryId = DefaultCategories.shopping.id),
                    expense(on = "2026-09-02", amountMinor = 1_000),
                ),
            )
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                val state = awaitItem() as InsightsUiState.Success

                assertEquals("$10.00", state.totalSpend)
                assertEquals(listOf(DefaultCategories.groceries.id), state.categories.map { it.categoryId })
            }
        }

    private fun viewModel(repository: TestExpenseRepository) =
        InsightsViewModel(
            expenseRepository = repository,
            categoryRepository = FakeCategoryRepository(DefaultCategories.all),
            budgetRepository = FakeBudgetRepository(),
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        )

    private fun expense(
        on: String,
        amountMinor: Long = 1_000,
        categoryId: String = DefaultCategories.groceries.id,
    ) = Expense(
        id = on,
        merchant = "Merchant",
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = LocalDate.parse(on),
        categoryId = categoryId,
        note = null,
        receiptImagePath = null,
        syncState = SyncState.SYNCED,
        updatedAt = Instant.EPOCH,
        isDeleted = false,
    )

    private class FakeCategoryRepository(
        private val categories: List<Category>,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = flowOf(categories)
    }

    private class FakeBudgetRepository : BudgetRepository {
        override fun observeBudgets(month: YearMonth): Flow<List<Budget>> = flowOf(emptyList())

        override suspend fun setBudget(
            categoryId: String,
            limitMinor: Long,
            currency: String,
            month: YearMonth,
        ) = Unit

        override suspend fun clearBudget(
            categoryId: String,
            month: YearMonth,
        ) = Unit
    }
}
