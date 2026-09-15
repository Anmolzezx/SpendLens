package com.spendlens.feature.expenses.list

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
        /**
         * Conflicts can exist with no visible expenses: an expense deleted here but edited on another
         * device is hidden from the list, yet still waits for a decision.
         */
        val conflictedExpenseIds: ImmutableList<String> = persistentListOf(),
        /** Shown here too: a new device's first sync is exactly when the list is still empty. */
        val syncStatus: SyncStatusUiModel = SyncStatusUiModel.NeverSynced,
    ) : ExpenseListUiState

    data class Success(
        val expenses: ImmutableList<ExpenseUiModel>,
        val monthTotal: String,
        /** Waiting to upload. Conflicts are not counted here; they need the user, not the network. */
        val pendingCount: Int,
        val conflictedExpenseIds: ImmutableList<String> = persistentListOf(),
        val syncStatus: SyncStatusUiModel = SyncStatusUiModel.NeverSynced,
    ) : ExpenseListUiState

    data class Error(
        val message: String,
    ) : ExpenseListUiState
}

/** Where this device stands with the server, as one line of text. */
sealed interface SyncStatusUiModel {
    data object Syncing : SyncStatusUiModel

    data object NeverSynced : SyncStatusUiModel

    /** [formattedTime] is just a time for today, and a date and time for anything older. */
    data class SyncedAt(
        val formattedTime: String,
    ) : SyncStatusUiModel
}
