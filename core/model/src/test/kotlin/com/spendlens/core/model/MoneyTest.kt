package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for [formatAsMoney].
 *
 * Every case passes an explicit [Locale]. Currency formatting is locale-dependent — symbol position,
 * grouping separator, decimal separator — so a test that read the ambient default would pass on a
 * developer machine and fail on a CI runner configured differently.
 */
class MoneyTest {
    @Test
    fun `formats USD as major units with two decimal places`() {
        assertEquals("$12.34", 1_234L.formatAsMoney("USD", Locale.US))
    }

    @Test
    fun `formats amounts under one major unit`() {
        assertEquals("$1.99", 199L.formatAsMoney("USD", Locale.US))
    }

    @Test
    fun `groups thousands`() {
        assertEquals("$2,400.00", 240_000L.formatAsMoney("USD", Locale.US))
    }

    @Test
    fun `formats zero`() {
        assertEquals("$0.00", 0L.formatAsMoney("USD", Locale.US))
    }

    /**
     * The regression this whole function exists for.
     *
     * JPY has zero minor units, so 1234 yen is ¥1,234 — not ¥12.34. A hardcoded `/ 100` passes every
     * USD test above and is silently wrong here. Asserted structurally rather than against an exact
     * string so a CLDR symbol change in a future JDK does not break the test for the wrong reason.
     */
    @Test
    fun `JPY has no minor units and is not divided by one hundred`() {
        val formatted = 1_234L.formatAsMoney("JPY", Locale.US)

        assertTrue("expected 1,234 in \"$formatted\"", formatted.contains("1,234"))
        assertFalse("must not treat JPY as having 2 decimals", formatted.contains("12.34"))
    }

    /** KWD has three minor units — the other direction of the same mistake. */
    @Test
    fun `KWD has three minor units`() {
        val formatted = 1_234L.formatAsMoney("KWD", Locale.US)

        assertTrue("expected 1.234 in \"$formatted\"", formatted.contains("1.234"))
    }

    /**
     * Proves the locale parameter is actually used, without pinning either output to CLDR data that
     * shifts between JDK releases.
     */
    @Test
    fun `respects the supplied locale`() {
        val us = 1_234L.formatAsMoney("EUR", Locale.US)
        val germany = 1_234L.formatAsMoney("EUR", Locale.GERMANY)

        assertNotEquals(us, germany)
    }

    @Test
    fun `totalMinor sums amounts`() {
        val total = totalMinor(listOf(sampleExpense(199), sampleExpense(1_234), sampleExpense(1)))

        assertEquals(1_434L, total)
    }

    @Test
    fun `totalMinor of an empty list is zero`() {
        assertEquals(0L, totalMinor(emptyList()))
    }

    private fun sampleExpense(amountMinor: Long) =
        Expense(
            id = "id-$amountMinor",
            merchant = "Merchant",
            amountMinor = amountMinor,
            currency = "USD",
            occurredOn = java.time.LocalDate.of(2026, 8, 1),
            categoryId = "cat",
            note = null,
            receiptImagePath = null,
            syncState = SyncState.SYNCED,
            updatedAt = java.time.Instant.EPOCH,
            isDeleted = false,
        )
}
