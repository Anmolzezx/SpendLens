package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MonthlySpendTest {
    private val utc = ZoneId.of("UTC")
    private val august = YearMonth.of(2026, 8)

    @Test
    fun `sums expenses in the month`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, at = "2026-08-02T10:00:00Z"),
            expense(amountMinor = 2_500, at = "2026-08-20T10:00:00Z"),
        )

        assertEquals(3_500L, monthlyTotalMinor(expenses, august, utc))
    }

    @Test
    fun `ignores expenses from other months`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, at = "2026-08-02T10:00:00Z"),
            expense(amountMinor = 9_999, at = "2026-07-31T10:00:00Z"),
            expense(amountMinor = 8_888, at = "2026-09-01T10:00:00Z"),
        )

        assertEquals(1_000L, monthlyTotalMinor(expenses, august, utc))
    }

    /** Tombstones are still rows in the table; they must never reach a total. */
    @Test
    fun `ignores soft-deleted expenses`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, at = "2026-08-02T10:00:00Z"),
            expense(amountMinor = 5_000, at = "2026-08-03T10:00:00Z", isDeleted = true),
        )

        assertEquals(1_000L, monthlyTotalMinor(expenses, august, utc))
    }

    /**
     * Pins the timezone ambiguity documented on [occurredIn]: the same instant falls in a different
     * month depending on where it is read. This is asserted, not fixed, because the model has not
     * decided yet — but if `occurredAt` ever becomes a `LocalDate`, this test is the one that must
     * change, and that makes the change deliberate rather than accidental.
     */
    @Test
    fun `an instant near midnight can land in a different month by timezone`() {
        val lateAugustUtc = expense(amountMinor = 1_000, at = "2026-08-31T23:00:00Z")

        assertEquals(1_000L, monthlyTotalMinor(listOf(lateAugustUtc), august, utc))
        assertEquals(
            0L,
            monthlyTotalMinor(listOf(lateAugustUtc), august, ZoneId.of("Asia/Kolkata")),
        )
    }

    @Test
    fun `groups spend by category`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, at = "2026-08-02T10:00:00Z", categoryId = "food"),
            expense(amountMinor = 500, at = "2026-08-03T10:00:00Z", categoryId = "food"),
            expense(amountMinor = 2_000, at = "2026-08-04T10:00:00Z", categoryId = "travel"),
        )

        val byCategory = monthlySpendByCategory(expenses, august, utc)

        assertEquals(mapOf("food" to 1_500L, "travel" to 2_000L), byCategory)
    }

    @Test
    fun `categorySpend orders by spend descending`() {
        val expenses = listOf(
            expense(amountMinor = 500, at = "2026-08-02T10:00:00Z", categoryId = "food"),
            expense(amountMinor = 9_000, at = "2026-08-03T10:00:00Z", categoryId = "travel"),
        )

        val result = categorySpend(expenses, budgets = emptyList(), month = august, zoneId = utc)

        assertEquals(listOf("travel", "food"), result.map { it.categoryId })
    }

    /** An untouched budget is information — "nothing spent of £300" is worth a row. */
    @Test
    fun `categorySpend includes budgeted categories with no spend`() {
        val result = categorySpend(
            expenses = emptyList(),
            budgets = listOf(budget("food", 30_000)),
            month = august,
            zoneId = utc,
        )

        assertEquals(1, result.size)
        assertEquals(0L, result.first().spentMinor)
        assertEquals(BudgetStatus.UNDER, result.first().status)
    }

    @Test
    fun `categorySpend leaves status null when there is no budget`() {
        val result = categorySpend(
            expenses = listOf(expense(amountMinor = 500, at = "2026-08-02T10:00:00Z")),
            budgets = emptyList(),
            month = august,
            zoneId = utc,
        )

        assertNull(result.first().status)
        assertNull(result.first().fractionOfBudget)
    }

    @Test
    fun `categorySpend ignores budgets from other months`() {
        val result = categorySpend(
            expenses = listOf(expense(amountMinor = 500, at = "2026-08-02T10:00:00Z", categoryId = "food")),
            budgets = listOf(budget("food", 30_000, YearMonth.of(2026, 7))),
            month = august,
            zoneId = utc,
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
        at: String,
        categoryId: String = "food",
        isDeleted: Boolean = false,
    ) = Expense(
        id = "id-$at-$amountMinor",
        merchant = "Merchant",
        amountMinor = amountMinor,
        currency = "USD",
        occurredAt = Instant.parse(at),
        categoryId = categoryId,
        note = null,
        receiptImagePath = null,
        syncState = SyncState.SYNCED,
        updatedAt = Instant.parse(at),
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
