package com.spendlens.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InsightsContent(
        uiState = uiState,
        onSetBudget = { categoryId, limitMinor ->
            viewModel.setBudget(categoryId, limitMinor, currency = DEFAULT_CURRENCY)
        },
        onClearBudget = viewModel::clearBudget,
        modifier = modifier,
    )
}

/** v1 is single-currency — §2 puts multi-currency FX out of scope. */
private const val DEFAULT_CURRENCY = "USD"
