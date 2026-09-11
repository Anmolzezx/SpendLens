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
 * Preview and screenshot fixtures.
 *
 * Timezone and locale are pinned rather than read from the device: a preview whose rendered date
 * depends on the machine is a screenshot test that fails in CI for no real reason.
 */
internal object ExpenseListPreviewData {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val locale: Locale = Locale.US

    val success = ExpenseListUiState.Success(
        expenses = SampleExpenses.all
            .sortedByDescending { it.occurredAt }
            .map { expense ->
                expense.toUiModel(
                    category = SampleCategories.byId[expense.categoryId],
                    zoneId = zone,
                    locale = locale,
                )
            }.toImmutableList(),
        monthTotal = totalMinor(SampleExpenses.all).formatAsMoney("USD", locale),
        pendingCount = SampleExpenses.all.count { it.syncState != SyncState.SYNCED },
    )

    val expenses = success.expenses
}
