package com.spendlens.feature.insights

import androidx.compose.runtime.Immutable
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.model.BudgetStatus
import kotlinx.collections.immutable.ImmutableList

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /** No expenses this month at all — different from having spend but no budgets. */
    data object Empty : InsightsUiState

    data class Success(
        val monthLabel: String,
        val totalSpend: String,
        val categories: ImmutableList<CategoryInsightUiModel>,
    ) : InsightsUiState
}

/**
 * Everything here is either already formatted or a plain value.
 *
 * Notably absent: the screen-reader sentence. It needs string resources, and a `Context` in a
 * ViewModel is how a class becomes untestable — the composable assembles it from these fields.
 */
@Immutable
data class CategoryInsightUiModel(
    val categoryId: String,
    /** Null when the category is unknown; the composable supplies the fallback label. */
    val name: String?,
    val colorIndex: Int,
    val spent: String,
    /** Null when the category has no budget for this month. */
    val limit: String?,
    val status: BudgetStatus?,
    val fractionOfBudget: Float?,
    /** Share of the month's total spend, 0f..1f. Drives the proportion bar. */
    val shareOfTotal: Float,
)

/**
 * Maps the domain's budget verdict onto a design-system [Tone].
 *
 * `core:designsystem` deliberately does not know what a budget is, so the translation lives here.
 * A category with no budget is neutral rather than green: "no budget set" is not "doing well".
 */
fun BudgetStatus?.toTone(): Tone =
    when (this) {
        BudgetStatus.UNDER -> Tone.POSITIVE
        BudgetStatus.NEAR -> Tone.WARNING
        BudgetStatus.OVER -> Tone.CRITICAL
        null -> Tone.NEUTRAL
    }
