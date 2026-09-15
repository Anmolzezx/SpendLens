package com.spendlens.core.testing.repository

import com.spendlens.core.data.repository.ExpenseConflictRepository
import com.spendlens.core.model.ExpenseConflict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * An in-memory [ExpenseConflictRepository] for ViewModel tests.
 *
 * Mirrors the real one where it matters to a screen: a decision removes the conflict, so observers
 * see it disappear, exactly as they do when the database row is deleted.
 */
class TestExpenseConflictRepository(
    initial: List<ExpenseConflict> = emptyList(),
) : ExpenseConflictRepository {
    private val conflicts = MutableStateFlow(initial.associateBy { it.local.id })

    /** Decisions made, in order, as `expenseId to keptLocal`. */
    val decisions = mutableListOf<Pair<String, Boolean>>()

    override fun observeConflict(expenseId: String): Flow<ExpenseConflict?> = conflicts.map { it[expenseId] }

    override fun observeConflictedExpenseIds(): Flow<List<String>> = conflicts.map { it.keys.toList() }

    override suspend fun keepLocal(expenseId: String) = decide(expenseId, keptLocal = true)

    override suspend fun keepRemote(expenseId: String) = decide(expenseId, keptLocal = false)

    private fun decide(
        expenseId: String,
        keptLocal: Boolean,
    ) {
        decisions += expenseId to keptLocal
        conflicts.update { it - expenseId }
    }
}
