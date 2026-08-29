package com.spendlens.feature.expenses.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.spendlens.feature.expenses.R
import java.time.ZoneId

/**
 * Stateful half of the detail screen.
 *
 * [expenseId] arrives as a parameter today; once there is a ViewModel it comes from
 * `savedStateHandle.toRoute<ExpenseDetailRoute>()` instead, and this signature loses it.
 */
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uncategorised = stringResource(R.string.expenses_uncategorised)
    val locale = LocalConfiguration.current.locales[0]

    val uiState = remember(expenseId, locale, uncategorised) {
        FakeExpenseDetail.stateFor(
            expenseId = expenseId,
            uncategorisedLabel = uncategorised,
            zoneId = ZoneId.systemDefault(),
            locale = locale,
        )
    }

    ExpenseDetailContent(
        uiState = uiState,
        onBack = onBack,
        onEditClick = onEditClick,
        onDeleteClick = onDeleteClick,
        modifier = modifier,
    )
}
