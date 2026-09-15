package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpenseConflictTest {
    private val base = Expense(
        id = "a",
        merchant = "Blue Bottle",
        amountMinor = 650,
        currency = "USD",
        occurredOn = LocalDate.parse("2026-09-15"),
        categoryId = "cat-dining",
        note = null,
        receiptImagePath = null,
        syncState = SyncState.CONFLICT,
        updatedAt = Instant.EPOCH,
        isDeleted = false,
    )

    @Test
    fun `reports only the fields that differ`() {
        val conflict = ExpenseConflict(
            local = base.copy(amountMinor = 700),
            remote = base.copy(amountMinor = 900, note = "Oat milk"),
        )

        assertEquals(setOf(ExpenseField.AMOUNT, ExpenseField.NOTE), conflict.differences)
    }

    /** Same number, different money: 650 US cents is not 650 yen. */
    @Test
    fun `a different currency is a different amount`() {
        val conflict = ExpenseConflict(local = base, remote = base.copy(currency = "JPY"))

        assertEquals(setOf(ExpenseField.AMOUNT), conflict.differences)
    }

    @Test
    fun `a deletion on one side is reported on its own`() {
        val conflict = ExpenseConflict(
            local = base.copy(isDeleted = true),
            remote = base.copy(merchant = "Blue Bottle Coffee", amountMinor = 900),
        )

        assertEquals(setOf(ExpenseField.DELETED), conflict.differences)
    }

    /** Edit times and sync bookkeeping are not something the user chooses between. */
    @Test
    fun `ignores edit time, sync state and the receipt photo`() {
        val conflict = ExpenseConflict(
            local = base.copy(updatedAt = Instant.parse("2026-09-15T08:00:00Z"), receiptImagePath = "receipts/a.jpg"),
            remote = base.copy(updatedAt = Instant.parse("2026-09-15T09:00:00Z"), syncState = SyncState.SYNCED),
        )

        assertEquals(emptySet<ExpenseField>(), conflict.differences)
    }
}
