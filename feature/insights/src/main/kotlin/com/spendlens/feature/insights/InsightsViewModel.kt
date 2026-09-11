package com.spendlens.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Category
import com.spendlens.core.model.CategorySpend
import com.spendlens.core.model.Expense
import com.spendlens.core.model.categorySpend
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.monthlyTotalMinor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        private val clock: Clock,
        private val zoneId: ZoneId,
        private val locale: Locale,
    ) : ViewModel() {
        private val month: YearMonth get() = YearMonth.now(clock.withZone(zoneId))

        val uiState: StateFlow<InsightsUiState> =
            combine(
                expenseRepository.observeExpensesIn(month, zoneId),
                categoryRepository.observeCategories(),
            ) { expenses, categories ->
                toUiState(expenses, categories.associateBy(Category::id))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = InsightsUiState.Loading,
            )

        private fun toUiState(
            expenses: List<Expense>,
            categories: Map<String, Category>,
        ): InsightsUiState {
            val currentMonth = month
            val totalMinor = monthlyTotalMinor(expenses, currentMonth, zoneId)
            if (totalMinor == 0L) return InsightsUiState.Empty

            val currency = expenses.first().currency
            // Budgets are not persisted yet, so nothing is joined here. When core:data grows a
            // BudgetRepository, this gains a third flow and every category picks up its limit.
            val spend = categorySpend(
                expenses = expenses,
                budgets = emptyList(),
                month = currentMonth,
                zoneId = zoneId,
            )

            return InsightsUiState.Success(
                monthLabel = currentMonth.format(DateTimeFormatter.ofPattern(MONTH_PATTERN, locale)),
                totalSpend = totalMinor.formatAsMoney(currency, locale),
                categories = spend
                    .map { it.toUiModel(totalMinor, currency, categories) }
                    .toImmutableList(),
            )
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
                status = status,
                fractionOfBudget = fractionOfBudget,
                shareOfTotal = if (totalMinor > 0L) spentMinor.toFloat() / totalMinor else 0f,
            )
        }

        private companion object {
            const val MONTH_PATTERN = "LLLL yyyy"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
