package com.spendlens.feature.expenses.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful half of the detail screen.
 *
 * The expense id is no longer a parameter: the ViewModel reads it from its `SavedStateHandle` via
 * `toRoute<ExpenseDetailRoute>()`, which is both typed and process-death-safe.
 */
@Composable
fun ExpenseDetailScreen(
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onReviewConflictClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle, not collectAsState: the latter keeps collecting — and keeps the
    // database query alive — while the app is in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpenseDetailContent(
        uiState = uiState,
        onBack = onBack,
        onEditClick = onEditClick,
        onReviewConflictClick = onReviewConflictClick,
        onDeleteClick = {
            viewModel.delete()
            onBack()
        },
        modifier = modifier,
    )
}
