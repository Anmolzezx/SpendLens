package com.spendlens.feature.expenses.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseConflictRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.monthlyTotalMinor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpenseListViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        categoryRepository: CategoryRepository,
        conflictRepository: ExpenseConflictRepository,
        private val clock: Clock,
        private val zoneId: ZoneId,
        private val locale: Locale,
    ) : ViewModel() {
        val uiState: StateFlow<ExpenseListUiState> =
            combine(
                expenseRepository.observeExpenses(),
                categoryRepository.observeCategories(),
                conflictRepository.observeConflictedExpenseIds(),
            ) { expenses, categories, conflictedIds ->
                toUiState(expenses, categories.associateBy(Category::id), conflictedIds.toImmutableList())
            }.stateIn(
                scope = viewModelScope,
                // 5s rather than Eagerly: collection survives a configuration change without
                // restarting, but stops when the app is backgrounded instead of querying forever.
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExpenseListUiState.Loading,
            )

        fun deleteExpense(id: String) {
            viewModelScope.launch { expenseRepository.delete(id) }
        }

        private fun toUiState(
            expenses: List<Expense>,
            categories: Map<String, Category>,
            conflictedIds: ImmutableList<String>,
        ): ExpenseListUiState {
            if (expenses.isEmpty()) {
                // Filtering is not implemented yet; when it is, this distinguishes "nothing yet"
                // from "nothing matches", which are different screens.
                return ExpenseListUiState.Empty(hasActiveFilters = false, conflictedExpenseIds = conflictedIds)
            }

            // The zone is still needed here, but only to know what "this month" is right now —
            // not to decide which month an expense belongs to.
            val thisMonth = YearMonth.now(clock.withZone(zoneId))
            val monthTotal = monthlyTotalMinor(expenses, thisMonth)

            return ExpenseListUiState.Success(
                expenses = expenses
                    .map { expense ->
                        expense.toUiModel(
                            category = categories[expense.categoryId],
                            locale = locale,
                        )
                    }.toImmutableList(),
                monthTotal = monthTotal.formatAsMoney(expenses.first().currency, locale),
                pendingCount = expenses.count { it.syncState == SyncState.PENDING },
                conflictedExpenseIds = conflictedIds,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
