package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boundary tests for [BudgetStatus.of].
 *
 * The interesting cases are all on the edges — exactly at the 80% threshold, and exactly at the
 * limit — because those are where an off-by-one turns "you are at your budget" into "you are over
 * it", which is a notification the user did not earn.
 */
class BudgetStatusTest {

    @Test
    fun `well below the limit is under`() {
        assertEquals(BudgetStatus.UNDER, BudgetStatus.of(spentMinor = 0, limitMinor = 1_000))
    }

    @Test
    fun `just below the threshold is still under`() {
        // 79.9% — one minor unit short of NEAR.
        assertEquals(BudgetStatus.UNDER, BudgetStatus.of(spentMinor = 799, limitMinor = 1_000))
    }

    @Test
    fun `exactly at the threshold is near`() {
        assertEquals(BudgetStatus.NEAR, BudgetStatus.of(spentMinor = 800, limitMinor = 1_000))
    }

    /** Spending your budget exactly is not overspending. */
    @Test
    fun `exactly at the limit is near, not over`() {
        assertEquals(BudgetStatus.NEAR, BudgetStatus.of(spentMinor = 1_000, limitMinor = 1_000))
    }

    @Test
    fun `one minor unit past the limit is over`() {
        assertEquals(BudgetStatus.OVER, BudgetStatus.of(spentMinor = 1_001, limitMinor = 1_000))
    }

    /** No budget set is not the same as an exceeded budget — guards against divide-by-zero too. */
    @Test
    fun `a zero limit is under rather than over`() {
        assertEquals(BudgetStatus.UNDER, BudgetStatus.of(spentMinor = 500, limitMinor = 0))
    }
}
