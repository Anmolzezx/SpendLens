package com.spendlens.core.testing.repository

import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.model.occurredIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth

/**
 * An in-memory [ExpenseRepository] for ViewModel tests.
 *
 * Backed by a `MutableStateFlow` rather than a mock, so a test can push a new list and assert that
 * the ViewModel re-emits — which is the behaviour that matters and the one a mock cannot express.
 *
 * The semantics deliberately mirror the real implementation: reads exclude tombstones and sort
 * newest-first, and writes stamp `PENDING` while keeping sync bookkeeping the caller does not own.
 * A fake that is more permissive than the real thing lets tests pass against behaviour production
 * does not have.
 */
class TestExpenseRepository(
    initial: List<Expense> = emptyList(),
    private val now: Instant = Instant.parse("2026-08-26T12:00:00Z"),
) : ExpenseRepository {
    private val backing = MutableStateFlow(initial)

    /** Push a new backing list, as though the database changed underneath an observer. */
    fun emit(expenses: List<Expense>) {
        backing.value = expenses
    }

    val current: List<Expense> get() = backing.value

    override fun observeExpenses(): Flow<List<Expense>> = backing.map { it.visible() }

    override fun observeExpense(id: String): Flow<Expense?> =
        backing.map { expenses -> expenses.visible().firstOrNull { it.id == id } }

    override fun observeExpensesIn(month: YearMonth): Flow<List<Expense>> =
        backing.map { expenses -> expenses.visible().filter { it.occurredIn(month) } }

    override fun observeExpensesInRange(
        firstMonth: YearMonth,
        lastMonth: YearMonth,
    ): Flow<List<Expense>> =
        backing.map { expenses ->
            expenses.visible().filter { YearMonth.from(it.occurredOn) in firstMonth..lastMonth }
        }

    override suspend fun upsert(expense: Expense) {
        val stored = backing.value.firstOrNull { it.id == expense.id }
        val stamped = expense.copy(
            syncState = stored.nextSyncState(),
            updatedAt = now,
            remoteVersion = stored?.remoteVersion,
        )
        backing.value = backing.value.filterNot { it.id == expense.id } + stamped
    }

    override suspend fun delete(id: String) {
        backing.value = backing.value.map { expense ->
            if (expense.id == id) {
                expense.copy(isDeleted = true, syncState = expense.nextSyncState(), updatedAt = now)
            } else {
                expense
            }
        }
    }

    /** As in the real repository: a local write makes a row pending, but never settles a conflict. */
    private fun Expense?.nextSyncState() =
        if (this?.syncState == SyncState.CONFLICT) SyncState.CONFLICT else SyncState.PENDING

    /** Same ordering as the DAO — newest day first, most recently edited first within a day. */
    private fun List<Expense>.visible() =
        filterNot { it.isDeleted }
            .sortedWith(compareByDescending<Expense> { it.occurredOn }.thenByDescending { it.updatedAt })
}
