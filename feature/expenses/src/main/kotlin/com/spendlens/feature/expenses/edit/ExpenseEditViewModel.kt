package com.spendlens.feature.expenses.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendlens.core.data.receipt.ReceiptImageStore
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.model.Expense
import com.spendlens.core.model.MoneyParseResult
import com.spendlens.core.model.SyncState
import com.spendlens.core.model.parseMoney
import com.spendlens.core.model.toAmountInput
import com.spendlens.feature.expenses.navigation.ExpenseEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExpenseEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val expenseRepository: ExpenseRepository,
        categoryRepository: CategoryRepository,
        private val clock: Clock,
        private val receiptImageStore: ReceiptImageStore,
    ) : ViewModel() {
        private val route: ExpenseEditRoute = savedStateHandle.toRoute()
        private val isNewExpense = route.expenseId == null
        private var saved = false

        // Seeded from the route so a scanned receipt arrives pre-filled. Anything the parser could
        // not read stays blank rather than being guessed at.
        private val form = MutableStateFlow(
            FormState(
                merchant = route.merchant.orEmpty(),
                amount = route.amountMinor?.toAmountInput(DEFAULT_CURRENCY).orEmpty(),
                occurredAtMillis = route.occurredAtMillis ?: clock.millis(),
                receiptImagePath = route.receiptImagePath,
            ),
        )

        val uiState: StateFlow<ExpenseEditUiState> =
            combine(form, categoryRepository.observeCategories()) { formState, categories ->
                ExpenseEditUiState(
                    isNewExpense = isNewExpense,
                    merchant = formState.merchant,
                    amount = formState.amount,
                    currency = formState.currency,
                    categoryId = formState.categoryId,
                    note = formState.note,
                    occurredAtMillis = formState.occurredAtMillis,
                    categories = categories.toImmutableList(),
                    showErrors = formState.showErrors,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExpenseEditUiState(
                    isNewExpense = isNewExpense,
                    merchant = "",
                    amount = "",
                    currency = DEFAULT_CURRENCY,
                    categoryId = null,
                    note = "",
                    occurredAtMillis = clock.millis(),
                    categories = emptyList<com.spendlens.core.model.Category>().toImmutableList(),
                    showErrors = false,
                ),
            )

        init {
            // Load once rather than observing: an edit form that reacts to the database overwriting
            // what the user is typing is a data-loss bug, not a feature.
            route.expenseId?.let { id ->
                viewModelScope.launch {
                    expenseRepository.observeExpense(id).first()?.let { existing ->
                        form.value = FormState(
                            merchant = existing.merchant,
                            amount = existing.amountMinor.toAmountInput(existing.currency),
                            currency = existing.currency,
                            categoryId = existing.categoryId,
                            note = existing.note.orEmpty(),
                            occurredAtMillis = existing.occurredAt.toEpochMilli(),
                            receiptImagePath = existing.receiptImagePath,
                        )
                    }
                }
            }
        }

        fun onMerchantChange(value: String) = form.update { it.copy(merchant = value) }

        fun onAmountChange(value: String) = form.update { it.copy(amount = value) }

        fun onCategoryChange(value: String) = form.update { it.copy(categoryId = value) }

        fun onNoteChange(value: String) = form.update { it.copy(note = value) }

        fun onDateChange(millis: Long) = form.update { it.copy(occurredAtMillis = millis) }

        /**
         * @return true when the expense was saved and the caller should navigate back. Invalid input
         *   reveals the errors instead — the save button stays tappable so it can say why.
         */
        fun save(): Boolean {
            val state = uiState.value
            if (!state.isValid) {
                form.update { it.copy(showErrors = true) }
                return false
            }

            val amountMinor = (parseMoney(state.amount, state.currency) as MoneyParseResult.Success).amountMinor
            saved = true
            viewModelScope.launch {
                expenseRepository.upsert(
                    Expense(
                        // Client-generated id: an offline-first record needs identity the moment it
                        // exists, not when a server first sees it.
                        id = route.expenseId ?: UUID.randomUUID().toString(),
                        merchant = state.merchant.trim(),
                        amountMinor = amountMinor,
                        currency = state.currency,
                        occurredAt = Instant.ofEpochMilli(state.occurredAtMillis),
                        categoryId = requireNotNull(state.categoryId),
                        note = state.note.trim().takeIf { it.isNotEmpty() },
                        receiptImagePath = form.value.receiptImagePath,
                        // Both stamped by the repository; these are placeholders.
                        syncState = SyncState.PENDING,
                        updatedAt = clock.instant(),
                        isDeleted = false,
                    ),
                )
            }
            return true
        }

        /**
         * A scan the user backed out of must not leave its photo behind.
         *
         * `onCleared` rather than a cancel callback, because it covers every way off the screen — the
         * Cancel button, system back, and predictive back — with one hook. It only fires when the
         * screen is really gone, not on rotation. The photo belongs to this form only while the
         * expense is new and unsaved; once saved, the expense row owns it.
         *
         * A single `unlink` on the main thread is deliberate: `viewModelScope` is already cancelled
         * here, and one small file delete is not worth an application-scoped coroutine.
         */
        override fun onCleared() {
            val abandonedScan = isNewExpense && !saved
            val imagePath = form.value.receiptImagePath
            if (abandonedScan && imagePath != null) {
                receiptImageStore.deleteQuietly(receiptImageStore.resolve(imagePath))
            }
            super.onCleared()
        }

        private data class FormState(
            val merchant: String = "",
            val amount: String = "",
            val currency: String = DEFAULT_CURRENCY,
            val categoryId: String? = null,
            val note: String = "",
            val occurredAtMillis: Long,
            val receiptImagePath: String? = null,
            val showErrors: Boolean = false,
        )

        private inline fun MutableStateFlow<FormState>.update(block: (FormState) -> FormState) {
            value = block(value)
        }

        private companion object {
            /** v1 is single-currency — §2 puts multi-currency FX out of scope. */
            const val DEFAULT_CURRENCY = "USD"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
