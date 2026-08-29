package com.spendlens.feature.expenses.list

import kotlinx.collections.immutable.ImmutableList

/**
 * A sealed interface rather than a single class with nullable fields: `when` becomes exhaustive, and
 * there is no way to represent the nonsense state of "loading, with an error, and also data".
 */
sealed interface ExpenseListUiState {
    data object Loading : ExpenseListUiState

    /**
     * @param hasActiveFilters distinguishes "you have no expenses yet" from "your filters matched
     *   nothing". They are different screens with different copy and different actions, and
     *   collapsing them into one empty state is a common way to strand a user.
     */
    data class Empty(
        val hasActiveFilters: Boolean,
    ) : ExpenseListUiState

    data class Success(
        val expenses: ImmutableList<ExpenseUiModel>,
        val monthTotal: String,
        val pendingCount: Int,
    ) : ExpenseListUiState

    data class Error(
        val message: String,
    ) : ExpenseListUiState
}
