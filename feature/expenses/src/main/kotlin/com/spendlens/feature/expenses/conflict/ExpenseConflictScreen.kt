package com.spendlens.feature.expenses.conflict

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExpenseConflictScreen(
    onBack: () -> Unit,
    onResolved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseConflictViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnResolved by rememberUpdatedState(onResolved)

    // Closing is driven by state, so it also happens if the conflict was settled before this opened.
    if (uiState == ExpenseConflictUiState.Resolved) {
        LaunchedEffect(Unit) { currentOnResolved() }
    }

    ExpenseConflictContent(
        uiState = uiState,
        onBack = onBack,
        onKeepThisDevice = viewModel::keepThisDevice,
        onKeepOtherDevice = viewModel::keepOtherDevice,
        modifier = modifier,
    )
}
