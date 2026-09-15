package com.spendlens.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.model.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * The seeding runs inside Room's creation transaction, so it either works on a real database or not
 * at all — there is no unit-testable seam. This builds an actual database and reads the table back.
 */
@RunWith(RobolectricTestRunner::class)
class SeedCategoriesCallbackTest {
    private lateinit var database: SpendLensDatabase

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                SpendLensDatabase::class.java,
            ).addCallback(SeedCategoriesCallback())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `seeds every default category on creation`() =
        runTest {
            val stored = database.categoryDao().observeCategories().first()

            assertEquals(DefaultCategories.all.size, stored.size)
            assertEquals(
                DefaultCategories.all.map { it.id }.sorted(),
                stored.map { it.id }.sorted(),
            )
        }

    @Test
    fun `seeded categories keep their colour index and icon key`() =
        runTest {
            val groceries = database.categoryDao().getCategory(DefaultCategories.groceries.id)

            assertEquals(DefaultCategories.groceries.name, groceries?.name)
            assertEquals(DefaultCategories.groceries.colorIndex, groceries?.colorIndex)
            assertEquals(DefaultCategories.groceries.iconKey, groceries?.iconKey)
        }

    /** An expense referencing a seeded category resolves — the join the list screen depends on. */
    @Test
    fun `an expense can reference a seeded category`() =
        runTest {
            database.expenseDao().upsert(
                com.spendlens.core.database.entity.ExpenseEntity(
                    id = "a",
                    merchant = "Trader Joe's",
                    amountMinor = 4_287,
                    currency = "USD",
                    occurredOn = java.time.LocalDate.parse("2026-08-26"),
                    categoryId = DefaultCategories.groceries.id,
                    note = null,
                    receiptImagePath = null,
                    syncState = com.spendlens.core.model.SyncState.PENDING,
                    updatedAt = Instant.EPOCH,
                    isDeleted = false,
                ),
            )

            val expense = database
                .expenseDao()
                .observeExpenses()
                .first()
                .single()
            val category = database.categoryDao().getCategory(expense.categoryId)

            assertEquals("Groceries", category?.name)
        }
}
