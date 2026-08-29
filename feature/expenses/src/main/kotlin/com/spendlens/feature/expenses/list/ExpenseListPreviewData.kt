package com.spendlens.feature.expenses.list

import com.spendlens.core.model.SyncState
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import com.spendlens.core.model.totalMinor
import kotlinx.collections.immutable.toImmutableList
import java.time.ZoneId
import java.util.Locale

/**
 * The fake data source standing in for the repository until phase 1's Room work lands.
 *
 * When the real repository arrives this becomes the fixture behind `FakeExpenseRepository` in
 * `core:testing`, and nothing in the UI changes.
 */
internal object FakeExpenseList {
    fun success(
        zoneId: ZoneId,
        locale: Locale,
        uncategorisedLabel: String,
    ): ExpenseListUiState.Success =
        ExpenseListUiState.Success(
            expenses = SampleExpenses.all
                // Newest first. Sorting here rather than trusting the fixture's declaration order
                // mirrors what the repository's `ORDER BY occurred_at DESC` will do.
                .sortedByDescending { it.occurredAt }
                .map { expense ->
                    expense.toUiModel(
                        category = SampleCategories.byId[expense.categoryId],
                        uncategorisedLabel = uncategorisedLabel,
                        zoneId = zoneId,
                        locale = locale,
                    )
                }.toImmutableList(),
            monthTotal = totalMinor(SampleExpenses.all).formatAsMoney(SAMPLE_CURRENCY, locale),
            pendingCount = SampleExpenses.all.count { it.syncState != SyncState.SYNCED },
        )

    private const val SAMPLE_CURRENCY = "USD"
}

/**
 * Preview and screenshot fixtures, with timezone and locale pinned.
 *
 * Reading the ambient defaults would make a rendered date depend on the machine, which turns a
 * Paparazzi run into a test that passes locally and fails in CI.
 */
internal object ExpenseListPreviewData {
    val success: ExpenseListUiState.Success = FakeExpenseList.success(
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        uncategorisedLabel = "Uncategorised",
    )

    val expenses = success.expenses
}
