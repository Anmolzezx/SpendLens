package com.spendlens.core.model.sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are load-bearing — previews, screenshot tests, and the fake repository all read them —
 * so their invariants are worth pinning. Without this, someone "fixing" a fixture can quietly break
 * every screenshot baseline at once.
 */
class SampleDataTest {
    @Test
    fun `visible expenses contain no tombstones`() {
        assertTrue(SampleExpenses.all.none { it.isDeleted })
    }

    @Test
    fun `the tombstone is excluded from the visible list`() {
        assertFalse(SampleExpenses.all.contains(SampleExpenses.tombstone))
        assertTrue(SampleExpenses.allIncludingDeleted.contains(SampleExpenses.tombstone))
    }

    @Test
    fun `ids are unique`() {
        val ids = SampleExpenses.allIncludingDeleted.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `every expense references a real category`() {
        val known = SampleCategories.byId.keys

        SampleExpenses.allIncludingDeleted.forEach { expense ->
            assertTrue(
                "unknown category ${expense.categoryId} on ${expense.id}",
                expense.categoryId in known,
            )
        }
    }

    /** The palette has six colours; an index outside it would wrap and silently reuse one. */
    @Test
    fun `category colour indices are distinct`() {
        val indices = SampleCategories.all.map { it.colorIndex }

        assertEquals(indices.size, indices.distinct().size)
    }

    @Test
    fun `every budget targets a real category`() {
        val known = SampleCategories.byId.keys

        SampleBudgets.all.forEach { budget ->
            assertTrue("unknown category ${budget.categoryId}", budget.categoryId in known)
        }
    }

    /**
     * The fixtures deliberately span all three sync states so the list screen renders every badge
     * variant without anyone hand-editing data to see them.
     */
    @Test
    fun `visible expenses cover every sync state`() {
        val states = SampleExpenses.all.map { it.syncState }.distinct()

        assertEquals(3, states.size)
    }
}
