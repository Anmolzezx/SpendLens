package com.spendlens.feature.insights

import com.spendlens.core.model.Expense
import com.spendlens.core.model.categorySpend
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.monthlyTotalMinor
import com.spendlens.core.model.monthlyTotals
import com.spendlens.core.model.sample.SampleBudgets
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import com.spendlens.core.model.toAmountInput
import com.spendlens.core.model.trailingMonths
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Preview fixtures, pinned to the month the sample data occupies and to [Locale.US].
 *
 * Unlike the ViewModel this joins [SampleBudgets], so previews exercise the under / near / over
 * meter states that the app itself cannot show until budgets are persisted.
 */
internal object InsightsPreviewData {
    private val month = YearMonth.of(2026, 8)
    private val locale: Locale = Locale.US
    private const val CURRENCY = "USD"

    val success: InsightsUiState = build()

    private fun build(): InsightsUiState {
        val expenses = SampleExpenses.allIncludingDeleted
        val totalMinor = monthlyTotalMinor(expenses, month)

        return InsightsUiState.Success(
            monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
            totalSpend = totalMinor.formatAsMoney(CURRENCY, locale),
            categories = categorySpend(expenses, SampleBudgets.all, month)
                .map { spend ->
                    val category = SampleCategories.byId[spend.categoryId]
                    CategoryInsightUiModel(
                        categoryId = spend.categoryId,
                        name = category?.name,
                        colorIndex = category?.colorIndex ?: 0,
                        spent = spend.spentMinor.formatAsMoney(CURRENCY, locale),
                        limit = spend.limitMinor?.formatAsMoney(CURRENCY, locale),
                        limitInput = spend.limitMinor?.toAmountInput(CURRENCY).orEmpty(),
                        currency = CURRENCY,
                        status = spend.status,
                        fractionOfBudget = spend.fractionOfBudget,
                        shareOfTotal = if (totalMinor > 0L) spend.spentMinor.toFloat() / totalMinor else 0f,
                    )
                }.toImmutableList(),
            trend = trend(expenses),
        )
    }

    private fun trend(expenses: List<Expense>): ImmutableList<MonthTrendUiModel> {
        val totals = monthlyTotals(expenses, trailingMonths(month, count = 6))
        val biggest = totals.maxOf { it.totalMinor }
        return totals
            .map { total ->
                MonthTrendUiModel(
                    label = total.month.format(DateTimeFormatter.ofPattern("LLL", locale)),
                    amount = total.totalMinor.formatAsMoney(CURRENCY, locale),
                    fraction = if (biggest > 0L) total.totalMinor.toFloat() / biggest else 0f,
                    isCurrentMonth = total.month == month,
                )
            }.toImmutableList()
    }
}
