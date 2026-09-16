package com.spendlens.core.data.repository

import com.spendlens.core.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Reads and writes expenses.
 *
 * Every read returns a [Flow]. The database is the single source of truth, so a screen observing it
 * updates the moment anything changes — a local edit, or a row arriving from sync — without the
 * writer having to know who is watching. That is what makes "save and go back" show the new row with
 * no result-passing between screens.
 */
interface ExpenseRepository {
    fun observeExpenses(): Flow<List<Expense>>

    fun observeExpense(id: String): Flow<Expense?>

    /**
     * No timezone parameter. It used to need one, when an expense's date was an instant and a
     * near-midnight expense belonged to different months in different zones. Dates are calendar
     * dates now, so a month contains exactly the expenses dated in it.
     */
    fun observeExpensesIn(month: YearMonth): Flow<List<Expense>>

    /**
     * Every expense from [firstMonth] to [lastMonth], both included — one query for a trend rather than
     * one per month.
     */
    fun observeExpensesInRange(
        firstMonth: YearMonth,
        lastMonth: YearMonth,
    ): Flow<List<Expense>>

    /**
     * Creates or replaces. The implementation stamps `updatedAt` and marks the row `PENDING`;
     * callers do not get to decide sync state, or they would eventually forget to set it.
     */
    suspend fun upsert(expense: Expense)

    /** Soft delete — the row becomes a tombstone so the deletion itself can sync. */
    suspend fun delete(id: String)
}
