package com.spendlens.feature.expenses.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful half of the list screen.
 *
 * This is the seam the UI-first phase was built around: the body changed from constructing fake
 * state to collecting the ViewModel's, and [ExpenseListContent] below it did not change at all.
 */
@Composable
fun ExpenseListScreen(
    onExpenseClick: (String) -> Unit,
    onReviewConflictClick: (String) -> Unit,
    onAddExpenseClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle, not collectAsState: the latter keeps collecting — and keeps the
    // database query alive — while the app is in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpenseListContent(
        uiState = uiState,
        onExpenseClick = onExpenseClick,
        onReviewConflictClick = onReviewConflictClick,
        onSyncNowClick = viewModel::syncNow,
        onAddExpenseClick = onAddExpenseClick,
        onScanReceiptClick = onScanReceiptClick,
        modifier = modifier,
    )
}
