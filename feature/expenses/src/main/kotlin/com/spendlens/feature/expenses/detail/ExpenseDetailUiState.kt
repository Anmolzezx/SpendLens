package com.spendlens.feature.expenses.detail

import androidx.compose.runtime.Immutable
import com.spendlens.core.model.SyncState

/**
 * [NotFound] is a first-class state, not an error.
 *
 * It is reachable in normal use: open a deep link to an expense someone deleted on another device,
 * or hit back into a detail screen for a row the sync just tombstoned. Treating it as a generic
 * failure would show "something went wrong" for something that went entirely right.
 */
sealed interface ExpenseDetailUiState {
    data object Loading : ExpenseDetailUiState

    data object NotFound : ExpenseDetailUiState

    data class Success(
        val expense: ExpenseDetailUiModel,
    ) : ExpenseDetailUiState
}

@Immutable
data class ExpenseDetailUiModel(
    val id: String,
    val merchant: String,
    val formattedAmount: String,
    val formattedDate: String,
    val categoryName: String?,
    val categoryColorIndex: Int,
    val note: String?,
    val receiptImagePath: String?,
    val syncState: SyncState,
)
