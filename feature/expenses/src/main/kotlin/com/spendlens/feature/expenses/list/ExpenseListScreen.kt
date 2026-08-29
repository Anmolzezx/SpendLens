package com.spendlens.feature.expenses.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.spendlens.feature.expenses.R
import java.time.ZoneId

/**
 * The stateful half of the list screen, and the only part of it `:app` can see.
 *
 * Today it hands the stateless screen fake data. When the data layer lands, the body becomes:
 *
 * ```
 * val viewModel: ExpenseListViewModel = hiltViewModel()
 * val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 * ```
 *
 * and nothing below this function changes — that is the whole point of building the UI first.
 */
@Composable
fun ExpenseListScreen(
    onExpenseClick: (String) -> Unit,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uncategorised = stringResource(R.string.expenses_uncategorised)
    val locale = LocalConfiguration.current.locales[0]

    // remember: formatting 18 rows on every recomposition would be wasted work, and this stands in
    // for a Flow that will only re-emit when the data actually changes.
    val uiState = remember(locale, uncategorised) {
        FakeExpenseList.success(
            zoneId = ZoneId.systemDefault(),
            locale = locale,
            uncategorisedLabel = uncategorised,
        )
    }

    ExpenseListContent(
        uiState = uiState,
        onExpenseClick = onExpenseClick,
        onAddExpenseClick = onAddExpenseClick,
        modifier = modifier,
    )
}
