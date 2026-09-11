package com.spendlens.feature.insights

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.component.AmountEmphasis
import com.spendlens.core.designsystem.component.AmountText
import com.spendlens.core.designsystem.component.EmptyState
import com.spendlens.core.designsystem.component.MeterBar
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.model.BudgetStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsContent(
    uiState: InsightsUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.insights_title)) }) },
    ) { padding ->
        when (uiState) {
            InsightsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            InsightsUiState.Empty -> EmptyState(
                title = stringResource(R.string.insights_empty_title),
                description = stringResource(R.string.insights_empty_description),
                modifier = Modifier.padding(padding),
            )

            is InsightsUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item { MonthHeader(uiState) }
                item {
                    Text(
                        text = stringResource(R.string.insights_by_category),
                        modifier = Modifier.padding(
                            start = Spacing.Large,
                            end = Spacing.Large,
                            top = Spacing.Small,
                            bottom = Spacing.Small,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items = uiState.categories, key = { it.categoryId }) { category ->
                    CategoryRow(category)
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    uiState: InsightsUiState.Success,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "${stringResource(R.string.insights_total_this_month)} · ${uiState.monthLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(
            amount = uiState.totalSpend,
            emphasis = AmountEmphasis.LARGE,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Built outside the SpendProportionBar call: joinToString's lambda is not a composable
        // scope, so stringResource cannot be invoked inside it.
        val descriptions = uiState.categories.map { it.stateDescription() }
        SpendProportionBar(
            segments = segmentsOf(uiState.categories),
            stateDescription = descriptions.joinToString(separator = ", "),
            modifier = Modifier.padding(top = Spacing.Small),
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryInsightUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.name ?: stringResource(R.string.insights_uncategorised),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AmountText(
                amount = category.spent,
                emphasis = AmountEmphasis.MEDIUM,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = category.limit
                ?.let { stringResource(R.string.insights_of_limit, it) }
                ?: stringResource(R.string.insights_no_budget),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MeterBar(
            // No budget means no meter to fill. The row still shows the amount and a dash of the
            // category colour, so an unbudgeted category is visible without pretending to a limit.
            fraction = category.fractionOfBudget ?: 0f,
            tone = category.status.toTone(),
            stateDescription = category.stateDescription(),
        )
    }
}

/**
 * The sentence TalkBack reads. Built here rather than in the ViewModel because it needs string
 * resources, and colour and bar length communicate nothing to a screen reader.
 */
@Composable
private fun CategoryInsightUiModel.stateDescription(): String {
    val label = name ?: stringResource(R.string.insights_uncategorised)
    return if (limit == null) {
        stringResource(R.string.insights_state_without_budget, label, spent)
    } else {
        stringResource(
            R.string.insights_state_with_budget,
            label,
            spent,
            limit,
            stringResource(status.labelRes()),
        )
    }
}

@StringRes
private fun BudgetStatus?.labelRes(): Int =
    when (this) {
        BudgetStatus.UNDER -> R.string.insights_status_under
        BudgetStatus.NEAR -> R.string.insights_status_near
        BudgetStatus.OVER -> R.string.insights_status_over
        null -> R.string.insights_no_budget
    }

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun InsightsSuccessPreview() {
    SpendLensTheme {
        Surface { InsightsContent(uiState = InsightsPreviewData.success) }
    }
}

@ThemePreviews
@Composable
private fun InsightsEmptyPreview() {
    SpendLensTheme {
        Surface { InsightsContent(uiState = InsightsUiState.Empty) }
    }
}

@ThemePreviews
@Composable
private fun InsightsLoadingPreview() {
    SpendLensTheme {
        Surface { InsightsContent(uiState = InsightsUiState.Loading) }
    }
}
