package com.spendlens.feature.expenses.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful half of the edit screen.
 *
 * The expense id comes from the ViewModel's `SavedStateHandle`, so form state now survives process
 * death as well as rotation — which the previous `rememberSaveable` version only half did.
 */
@Composable
fun ExpenseEditScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpenseEditContent(
        uiState = uiState,
        onMerchantChange = viewModel::onMerchantChange,
        onAmountChange = viewModel::onAmountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onNoteChange = viewModel::onNoteChange,
        onDateChange = viewModel::onDateChange,
        onSave = { if (viewModel.save()) onSaved() },
        onCancel = onCancel,
        modifier = modifier,
    )
}
