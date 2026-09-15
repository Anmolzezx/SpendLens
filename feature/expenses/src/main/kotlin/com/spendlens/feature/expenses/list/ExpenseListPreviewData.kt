package com.spendlens.feature.expenses.list

import com.spendlens.core.model.SyncState
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import com.spendlens.core.model.totalMinor
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * Preview and screenshot fixtures.
 *
 * The locale is pinned rather than read from the device: a preview whose rendered date depends on
 * the machine is a screenshot test that fails in CI for no real reason. No timezone to pin — dates
 * are calendar dates.
 */
internal object ExpenseListPreviewData {
    private val locale: Locale = Locale.US

    val success = ExpenseListUiState.Success(
        expenses = SampleExpenses.all
            // Same ordering as the DAO: newest day first, most recently edited first within a day.
            .sortedWith(
                compareByDescending<com.spendlens.core.model.Expense> {
                    it.occurredOn
                }.thenByDescending { it.updatedAt },
            ).map { expense ->
                expense.toUiModel(
                    category = SampleCategories.byId[expense.categoryId],
                    locale = locale,
                )
            }.toImmutableList(),
        monthTotal = totalMinor(SampleExpenses.all).formatAsMoney("USD", locale),
        pendingCount = SampleExpenses.all.count { it.syncState != SyncState.SYNCED },
    )

    val expenses = success.expenses
}
