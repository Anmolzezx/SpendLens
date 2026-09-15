package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MonthlySpendTest {
    private val august = YearMonth.of(2026, 8)

    @Test
    fun `sums expenses in the month`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-08-02"),
            expense(amountMinor = 2_500, on = "2026-08-20"),
        )

        assertEquals(3_500L, monthlyTotalMinor(expenses, august))
    }

    @Test
    fun `ignores expenses from other months`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-08-02"),
            expense(amountMinor = 9_999, on = "2026-07-31"),
            expense(amountMinor = 8_888, on = "2026-09-01"),
        )

        assertEquals(1_000L, monthlyTotalMinor(expenses, august))
    }

    /** Tombstones are still rows in the table; they must never reach a total. */
    @Test
    fun `ignores soft-deleted expenses`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-08-02"),
            expense(amountMinor = 5_000, on = "2026-08-03", isDeleted = true),
        )

        assertEquals(1_000L, monthlyTotalMinor(expenses, august))
    }

    /**
     * This replaced a test that asserted the opposite. While `occurredAt` was an `Instant`, 23:00 UTC
     * on 31 August counted toward August in UTC and September in Kolkata — the test pinned that
     * ambiguity and said it must change if the date ever became a `LocalDate`. It did, on 2026-09-15.
     */
    @Test
    fun `the last day of a month always belongs to that month`() {
        val lastDayOfAugust = expense(amountMinor = 1_000, on = "2026-08-31")

        assertEquals(1_000L, monthlyTotalMinor(listOf(lastDayOfAugust), august))
        assertEquals(0L, monthlyTotalMinor(listOf(lastDayOfAugust), YearMonth.of(2026, 9)))
    }

    @Test
    fun `groups spend by category`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-08-02", categoryId = "food"),
            expense(amountMinor = 500, on = "2026-08-03", categoryId = "food"),
            expense(amountMinor = 2_000, on = "2026-08-04", categoryId = "travel"),
        )

        val byCategory = monthlySpendByCategory(expenses, august)

        assertEquals(mapOf("food" to 1_500L, "travel" to 2_000L), byCategory)
    }

    @Test
    fun `categorySpend orders by spend descending`() {
        val expenses = listOf(
            expense(amountMinor = 500, on = "2026-08-02", categoryId = "food"),
            expense(amountMinor = 9_000, on = "2026-08-03", categoryId = "travel"),
        )

        val result = categorySpend(expenses, budgets = emptyList(), month = august)

        assertEquals(listOf("travel", "food"), result.map { it.categoryId })
    }

    /** An untouched budget is information — "nothing spent of £300" is worth a row. */
    @Test
    fun `categorySpend includes budgeted categories with no spend`() {
        val result = categorySpend(
            expenses = emptyList(),
            budgets = listOf(budget("food", 30_000)),
            month = august,
        )

        assertEquals(1, result.size)
        assertEquals(0L, result.first().spentMinor)
        assertEquals(BudgetStatus.UNDER, result.first().status)
    }

    @Test
    fun `categorySpend leaves status null when there is no budget`() {
        val result = categorySpend(
            expenses = listOf(expense(amountMinor = 500, on = "2026-08-02")),
            budgets = emptyList(),
            month = august,
        )

        assertNull(result.first().status)
        assertNull(result.first().fractionOfBudget)
    }

    @Test
    fun `categorySpend ignores budgets from other months`() {
        val result = categorySpend(
            expenses = listOf(expense(amountMinor = 500, on = "2026-08-02", categoryId = "food")),
            budgets = listOf(budget("food", 30_000, YearMonth.of(2026, 7))),
            month = august,
        )

        assertNull("a July budget must not apply to August", result.first().limitMinor)
    }

    @Test
    fun `fractionOfBudget guards against a zero limit`() {
        val result = CategorySpend(categoryId = "food", spentMinor = 500, limitMinor = 0)

        assertNull(result.fractionOfBudget)
        assertTrue(result.status == BudgetStatus.UNDER)
    }

    private fun expense(
        amountMinor: Long,
        on: String,
        categoryId: String = "food",
        isDeleted: Boolean = false,
    ) = Expense(
        id = "id-$on-$amountMinor",
        merchant = "Merchant",
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = LocalDate.parse(on),
        categoryId = categoryId,
        note = null,
        receiptImagePath = null,
        syncState = SyncState.SYNCED,
        updatedAt = Instant.EPOCH,
        isDeleted = isDeleted,
    )

    private fun budget(
        categoryId: String,
        limitMinor: Long,
        month: YearMonth = YearMonth.of(2026, 8),
    ) = Budget(
        categoryId = categoryId,
        limitMinor = limitMinor,
        currency = "USD",
        month = month,
    )
}
