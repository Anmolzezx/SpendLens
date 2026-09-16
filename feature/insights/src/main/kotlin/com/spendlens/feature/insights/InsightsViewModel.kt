package com.spendlens.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.core.data.repository.BudgetRepository
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Budget
import com.spendlens.core.model.Category
import com.spendlens.core.model.CategorySpend
import com.spendlens.core.model.Expense
import com.spendlens.core.model.MonthTotal
import com.spendlens.core.model.categorySpend
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.monthlyTotalMinor
import com.spendlens.core.model.monthlyTotals
import com.spendlens.core.model.toAmountInput
import com.spendlens.core.model.trailingMonths
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        expenseRepository: ExpenseRepository,
        categoryRepository: CategoryRepository,
        private val budgetRepository: BudgetRepository,
        private val clock: Clock,
        private val zoneId: ZoneId,
        private val locale: Locale,
    ) : ViewModel() {
        private val month: YearMonth get() = YearMonth.now(clock.withZone(zoneId))

        val uiState: StateFlow<InsightsUiState> =
            combine(
                // The whole trend window in one query; this month is a filter over the result rather
                // than a second query for the same rows.
                expenseRepository.observeExpensesInRange(trailingMonths(month, TREND_MONTHS).first(), month),
                categoryRepository.observeCategories(),
                budgetRepository.observeBudgets(month),
            ) { expenses, categories, budgets ->
                toUiState(expenses, categories.associateBy(Category::id), budgets)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = InsightsUiState.Loading,
            )

        private fun toUiState(
            expenses: List<Expense>,
            categories: Map<String, Category>,
            budgets: List<Budget>,
        ): InsightsUiState {
            val currentMonth = month
            val totals = monthlyTotals(expenses, trailingMonths(currentMonth, TREND_MONTHS))
            // Empty only when nothing was spent in the whole window. A quiet month still has a trend
            // worth showing — and the screen used to hide six months of history because of one.
            if (totals.all { it.totalMinor == 0L }) return InsightsUiState.Empty

            val currency = expenses.first().currency
            val totalMinor = monthlyTotalMinor(expenses, currentMonth)
            val spend = categorySpend(
                expenses = expenses,
                budgets = budgets,
                month = currentMonth,
            )

            return InsightsUiState.Success(
                monthLabel = currentMonth.format(DateTimeFormatter.ofPattern(MONTH_PATTERN, locale)),
                totalSpend = totalMinor.formatAsMoney(currency, locale),
                categories = spend
                    .map { it.toUiModel(totalMinor, currency, categories) }
                    .toImmutableList(),
                trend = totals.toTrend(currency, currentMonth),
            )
        }

        /** Bars are relative to the biggest month in the window, so the tallest is always full height. */
        private fun List<MonthTotal>.toTrend(
            currency: String,
            currentMonth: YearMonth,
        ) = maxOf { it.totalMinor }.let { biggest ->
            map { total ->
                MonthTrendUiModel(
                    label = total.month.format(DateTimeFormatter.ofPattern(SHORT_MONTH_PATTERN, locale)),
                    amount = total.totalMinor.formatAsMoney(currency, locale),
                    fraction = if (biggest > 0L) total.totalMinor.toFloat() / biggest else 0f,
                    isCurrentMonth = total.month == currentMonth,
                )
            }.toImmutableList()
        }

        /**
         * Budgets are stored per month, and this only ever writes the month on screen — so editing
         * a limit in September cannot silently rewrite August's history.
         */
        fun setBudget(
            categoryId: String,
            limitMinor: Long,
            currency: String,
        ) {
            viewModelScope.launch {
                budgetRepository.setBudget(categoryId, limitMinor, currency, month)
            }
        }

        fun clearBudget(categoryId: String) {
            viewModelScope.launch { budgetRepository.clearBudget(categoryId, month) }
        }

        private fun CategorySpend.toUiModel(
            totalMinor: Long,
            currency: String,
            categories: Map<String, Category>,
        ): CategoryInsightUiModel {
            val category = categories[categoryId]
            return CategoryInsightUiModel(
                categoryId = categoryId,
                name = category?.name,
                colorIndex = category?.colorIndex ?: 0,
                spent = spentMinor.formatAsMoney(currency, locale),
                limit = limitMinor?.formatAsMoney(currency, locale),
                limitInput = limitMinor?.toAmountInput(currency).orEmpty(),
                currency = currency,
                status = status,
                fractionOfBudget = fractionOfBudget,
                shareOfTotal = if (totalMinor > 0L) spentMinor.toFloat() / totalMinor else 0f,
            )
        }

        private companion object {
            /** Half a year: enough to see a habit on a phone-width chart without squeezing the bars. */
            const val TREND_MONTHS = 6
            const val MONTH_PATTERN = "LLLL yyyy"
            const val SHORT_MONTH_PATTERN = "LLL"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
