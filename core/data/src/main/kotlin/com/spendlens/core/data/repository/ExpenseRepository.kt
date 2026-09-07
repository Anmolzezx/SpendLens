package com.spendlens.core.data.repository

import com.spendlens.core.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import java.time.ZoneId

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
     * @param zoneId decides which month a near-midnight expense belongs to. Explicit rather than
     *   assumed — see the open question in DECISIONS.md.
     */
    fun observeExpensesIn(
        month: YearMonth,
        zoneId: ZoneId,
    ): Flow<List<Expense>>

    /**
     * Creates or replaces. The implementation stamps `updatedAt` and marks the row `PENDING`;
     * callers do not get to decide sync state, or they would eventually forget to set it.
     */
    suspend fun upsert(expense: Expense)

    /** Soft delete — the row becomes a tombstone so the deletion itself can sync. */
    suspend fun delete(id: String)
}
