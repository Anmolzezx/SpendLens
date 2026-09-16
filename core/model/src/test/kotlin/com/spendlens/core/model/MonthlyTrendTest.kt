package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class MonthlyTrendTest {
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `trailing months end with the month asked for, oldest first`() {
        val months = trailingMonths(endMonth = september, count = 3)

        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), september), months)
    }

    @Test
    fun `trailing months cross a year boundary`() {
        val months = trailingMonths(endMonth = YearMonth.of(2027, 1), count = 3)

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), months)
    }

    @Test
    fun `totals follow the months given, in order`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-08-02"),
            expense(amountMinor = 500, on = "2026-08-30"),
            expense(amountMinor = 250, on = "2026-09-15"),
        )

        val totals = monthlyTotals(expenses, trailingMonths(september, count = 3))

        assertEquals(listOf(0L, 1_500L, 250L), totals.map { it.totalMinor })
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), september), totals.map { it.month })
    }

    /** A gap is information: dropping it would put August next to June as though they were adjacent. */
    @Test
    fun `a month with no spending is zero, not missing`() {
        val totals = monthlyTotals(listOf(expense(on = "2026-09-15")), trailingMonths(september, count = 2))

        assertEquals(2, totals.size)
        assertEquals(0L, totals.first().totalMinor)
    }

    @Test
    fun `deleted expenses do not count`() {
        val expenses = listOf(
            expense(amountMinor = 1_000, on = "2026-09-02"),
            expense(amountMinor = 9_999, on = "2026-09-03").copy(isDeleted = true),
        )

        assertEquals(1_000L, monthlyTotals(expenses, listOf(september)).single().totalMinor)
    }

    @Test
    fun `months outside the window are ignored`() {
        val expenses = listOf(expense(amountMinor = 9_999, on = "2026-01-05"))

        assertEquals(0L, monthlyTotals(expenses, listOf(september)).single().totalMinor)
    }

    private fun expense(
        amountMinor: Long = 1_000,
        on: String,
    ) = Expense(
        id = on,
        merchant = "Merchant",
        amountMinor = amountMinor,
        currency = "USD",
        occurredOn = LocalDate.parse(on),
        categoryId = "cat-groceries",
        note = null,
        receiptImagePath = null,
        syncState = SyncState.SYNCED,
        updatedAt = Instant.EPOCH,
        isDeleted = false,
    )
}
