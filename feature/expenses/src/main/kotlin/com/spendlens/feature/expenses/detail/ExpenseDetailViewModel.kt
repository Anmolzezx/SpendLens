package com.spendlens.feature.expenses.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendlens.core.data.receipt.ReceiptImageStore
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Category
import com.spendlens.feature.expenses.navigation.ExpenseDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpenseDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val expenseRepository: ExpenseRepository,
        categoryRepository: CategoryRepository,
        private val receiptImageStore: ReceiptImageStore,
        private val locale: Locale,
    ) : ViewModel() {
        /**
         * Typed route arguments straight off the `SavedStateHandle` — no string keys, no manual
         * null handling, and it survives process death because Navigation put it there.
         */
        private val route: ExpenseDetailRoute = savedStateHandle.toRoute()

        val uiState: StateFlow<ExpenseDetailUiState> =
            combine(
                expenseRepository.observeExpense(route.expenseId),
                categoryRepository.observeCategories(),
            ) { expense, categories ->
                if (expense == null) {
                    // Reachable in normal use: deep link to something another device deleted.
                    ExpenseDetailUiState.NotFound
                } else {
                    ExpenseDetailUiState.Success(
                        expense.toDetailUiModel(
                            category = categories.associateBy(Category::id)[expense.categoryId],
                            locale = locale,
                            // Rows store paths relative to filesDir; only ReceiptImageStore knows
                            // how to turn one back into a file, so the rule lives in one place.
                            resolveReceipt = receiptImageStore::resolve,
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExpenseDetailUiState.Loading,
            )

        fun delete() {
            viewModelScope.launch { expenseRepository.delete(route.expenseId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
