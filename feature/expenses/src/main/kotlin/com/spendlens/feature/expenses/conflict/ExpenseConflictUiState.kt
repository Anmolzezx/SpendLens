package com.spendlens.feature.expenses.conflict

import androidx.compose.runtime.Immutable
import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.ExpenseConflict
import com.spendlens.core.model.ExpenseField
import com.spendlens.core.model.formatAsMoney
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

sealed interface ExpenseConflictUiState {
    data object Loading : ExpenseConflictUiState

    /**
     * Nothing left to decide, so the screen closes. Reached right after the user chooses, but also if
     * a conflict was settled some other way while the screen was opening — a deep link to an old
     * conflict, or a second tap on "Review".
     */
    data object Resolved : ExpenseConflictUiState

    data class Deciding(
        val conflict: ExpenseConflictUiModel,
        /** True once a choice is on its way to the database; both buttons stop accepting taps. */
        val isSaving: Boolean,
    ) : ExpenseConflictUiState
}

@Immutable
data class ExpenseConflictUiModel(
    val thisDevice: ExpenseVersionUiModel,
    val otherDevice: ExpenseVersionUiModel,
    val differences: ImmutableSet<ExpenseField>,
)

@Immutable
data class ExpenseVersionUiModel(
    val merchant: String,
    val formattedAmount: String,
    val formattedDate: String,
    /** Null when the category no longer exists; the composable supplies "Uncategorised". */
    val categoryName: String?,
    val categoryColorIndex: Int,
    val note: String?,
    val isDeleted: Boolean,
    /**
     * When this version was saved, by the saving device's clock. Shown because "which one is newer?" is
     * the first thing people ask — but it is the user's call, not a rule: clocks drift, and the newer
     * edit is not always the right one.
     */
    val formattedEditedAt: String,
)

internal fun ExpenseConflict.toUiModel(
    categories: Map<String, Category>,
    zoneId: ZoneId,
    locale: Locale,
) = ExpenseConflictUiModel(
    thisDevice = local.toVersionUiModel(categories, zoneId, locale),
    otherDevice = remote.toVersionUiModel(categories, zoneId, locale),
    differences = differences.toImmutableSet(),
)

private fun Expense.toVersionUiModel(
    categories: Map<String, Category>,
    zoneId: ZoneId,
    locale: Locale,
): ExpenseVersionUiModel {
    val category = categories[categoryId]
    return ExpenseVersionUiModel(
        merchant = merchant,
        formattedAmount = amountMinor.formatAsMoney(currency, locale),
        formattedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(occurredOn),
        categoryName = category?.name,
        categoryColorIndex = category?.colorIndex ?: 0,
        note = note,
        isDeleted = isDeleted,
        // An instant does need a zone to be shown: this one is "when", not "which day".
        formattedEditedAt = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(updatedAt.atZone(zoneId)),
    )
}
