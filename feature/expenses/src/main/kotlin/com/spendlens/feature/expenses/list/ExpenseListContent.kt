package com.spendlens.feature.expenses.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.component.AmountEmphasis
import com.spendlens.core.designsystem.component.AmountText
import com.spendlens.core.designsystem.component.EmptyState
import com.spendlens.core.designsystem.component.StatusBadge
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.feature.expenses.R

/**
 * Stateless by design (§8): it takes a [uiState] and emits events, so it can be rendered from a
 * preview, a Paparazzi test, or a Compose UI test without a ViewModel, a repository, or Hilt.
 *
 * The stateful `ExpenseListRoute` that owns the ViewModel sits above this and changes nothing below
 * it — which is what makes the current fake-data wiring throwaway rather than rework.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseListContent(
    uiState: ExpenseListUiState,
    onExpenseClick: (String) -> Unit,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.expenses_title)) })
        },
        floatingActionButton = {
            // The content overload, not the text/icon one: passing `icon = {}` still reserves the
            // icon slot and its spacing, which left the label visibly off-centre.
            ExtendedFloatingActionButton(onClick = onAddExpenseClick) {
                Text(stringResource(R.string.expenses_add))
            }
        },
    ) { padding ->
        when (uiState) {
            ExpenseListUiState.Loading -> LoadingState(Modifier.padding(padding))

            is ExpenseListUiState.Empty -> EmptyState(
                title = stringResource(
                    if (uiState.hasActiveFilters) {
                        R.string.expenses_empty_filtered_title
                    } else {
                        R.string.expenses_empty_title
                    },
                ),
                description = stringResource(
                    if (uiState.hasActiveFilters) {
                        R.string.expenses_empty_filtered_description
                    } else {
                        R.string.expenses_empty_description
                    },
                ),
                modifier = Modifier.padding(padding),
                // Filtered-empty offers no action: the user has data, they just narrowed past it.
                actionLabel = stringResource(R.string.expenses_empty_action)
                    .takeUnless { uiState.hasActiveFilters },
                onAction = onAddExpenseClick.takeUnless { uiState.hasActiveFilters },
            )

            is ExpenseListUiState.Error -> EmptyState(
                title = stringResource(R.string.expenses_error_title),
                description = uiState.message,
                modifier = Modifier.padding(padding),
            )

            is ExpenseListUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // contentPadding, not Modifier.padding: under edge-to-edge this lets content scroll
                // beneath the system bars instead of clipping the scroll area short of them.
                //
                // Scaffold's inner padding does not account for the FAB, so the extra bottom space
                // is what stops the last row from sitting permanently underneath it.
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + FabClearance,
                ),
            ) {
                item { MonthSummary(uiState) }
                items(
                    items = uiState.expenses,
                    // Stable keys — required for correct item animations and scroll restoration.
                    key = { it.id },
                ) { expense ->
                    ExpenseRow(
                        expense = expense,
                        onClick = { onExpenseClick(expense.id) },
                        modifier = Modifier.animateItem(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MonthSummary(
    uiState: ExpenseListUiState.Success,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = stringResource(R.string.expenses_month_total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(
            amount = uiState.monthTotal,
            emphasis = AmountEmphasis.LARGE,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (uiState.pendingCount > 0) {
            StatusBadge(
                label = pluralStringResource(
                    R.plurals.expenses_pending_count,
                    uiState.pendingCount,
                    uiState.pendingCount,
                ),
                tone = Tone.WARNING,
            )
        }
    }
}

/** FAB height (56dp) plus its 16dp margins, so the last row can scroll clear of it. */
private val FabClearance = 88.dp

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — one per state. "No blank screens" (§9, phase 4) is only true if every state is built,
// and building them now costs minutes rather than a retrofit later.
// ---------------------------------------------------------------------------------------------

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun ExpenseListSuccessPreview() {
    SpendLensTheme {
        Surface {
            ExpenseListContent(
                uiState = ExpenseListPreviewData.success,
                onExpenseClick = {},
                onAddExpenseClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseListEmptyPreview() {
    SpendLensTheme {
        Surface {
            ExpenseListContent(
                uiState = ExpenseListUiState.Empty(hasActiveFilters = false),
                onExpenseClick = {},
                onAddExpenseClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseListFilteredEmptyPreview() {
    SpendLensTheme {
        Surface {
            ExpenseListContent(
                uiState = ExpenseListUiState.Empty(hasActiveFilters = true),
                onExpenseClick = {},
                onAddExpenseClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseListLoadingPreview() {
    SpendLensTheme {
        Surface {
            ExpenseListContent(
                uiState = ExpenseListUiState.Loading,
                onExpenseClick = {},
                onAddExpenseClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseListErrorPreview() {
    SpendLensTheme {
        Surface {
            ExpenseListContent(
                uiState = ExpenseListUiState.Error("No connection, and nothing cached yet."),
                onExpenseClick = {},
                onAddExpenseClick = {},
            )
        }
    }
}
