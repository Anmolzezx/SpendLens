package com.spendlens.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.data.repository.OfflineFirstBudgetRepository
import com.spendlens.core.database.SpendLensDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineFirstBudgetRepositoryTest {
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)

    private lateinit var database: SpendLensDatabase
    private lateinit var repository: OfflineFirstBudgetRepository

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
            ).build()
        repository = OfflineFirstBudgetRepository(
            budgetDao = database.budgetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores and reads back a budget`() =
        runTest {
            repository.setBudget("cat-groceries", 30_000, "USD", august)

            val stored = repository.observeBudgets(august).first().single()

            assertEquals("cat-groceries", stored.categoryId)
            assertEquals(30_000L, stored.limitMinor)
            assertEquals(august, stored.month)
        }

    /** The composite primary key makes "one budget per category per month" a database constraint. */
    @Test
    fun `setting a budget twice replaces rather than duplicates`() =
        runTest {
            repository.setBudget("cat-groceries", 30_000, "USD", august)
            repository.setBudget("cat-groceries", 45_000, "USD", august)

            val stored = repository.observeBudgets(august).first()

            assertEquals(1, stored.size)
            assertEquals(45_000L, stored.single().limitMinor)
        }

    /** Editing September's limit must not rewrite August's history. */
    @Test
    fun `budgets are scoped to their month`() =
        runTest {
            repository.setBudget("cat-groceries", 30_000, "USD", august)
            repository.setBudget("cat-groceries", 45_000, "USD", september)

            assertEquals(
                30_000L,
                repository
                    .observeBudgets(august)
                    .first()
                    .single()
                    .limitMinor,
            )
            assertEquals(
                45_000L,
                repository
                    .observeBudgets(september)
                    .first()
                    .single()
                    .limitMinor,
            )
        }

    @Test
    fun `clearing removes only that category's budget`() =
        runTest {
            repository.setBudget("cat-groceries", 30_000, "USD", august)
            repository.setBudget("cat-dining", 15_000, "USD", august)

            repository.clearBudget("cat-groceries", august)

            val remaining = repository.observeBudgets(august).first()
            assertEquals(listOf("cat-dining"), remaining.map { it.categoryId })
        }

    @Test
    fun `clearing a month leaves other months untouched`() =
        runTest {
            repository.setBudget("cat-groceries", 30_000, "USD", august)
            repository.setBudget("cat-groceries", 45_000, "USD", september)

            repository.clearBudget("cat-groceries", august)

            assertTrue(repository.observeBudgets(august).first().isEmpty())
            assertEquals(1, repository.observeBudgets(september).first().size)
        }
}
