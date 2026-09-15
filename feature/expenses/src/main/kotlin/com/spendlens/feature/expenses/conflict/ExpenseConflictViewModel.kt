package com.spendlens.feature.expenses.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseConflictRepository
import com.spendlens.core.model.Category
import com.spendlens.feature.expenses.navigation.ExpenseConflictRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * Shows both versions of a conflicted expense and records which one the user keeps.
 *
 * There is no "done" event. Deciding deletes the conflict, the observed conflict becomes null, and the
 * state becomes [ExpenseConflictUiState.Resolved] — the same path as a conflict settled any other
 * way, so the screen closes for one reason, not two.
 */
@HiltViewModel
class ExpenseConflictViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val conflictRepository: ExpenseConflictRepository,
        categoryRepository: CategoryRepository,
        private val zoneId: ZoneId,
        private val locale: Locale,
    ) : ViewModel() {
        private val expenseId = savedStateHandle.toRoute<ExpenseConflictRoute>().expenseId
        private val isSaving = MutableStateFlow(false)

        val uiState: StateFlow<ExpenseConflictUiState> =
            combine(
                conflictRepository.observeConflict(expenseId),
                categoryRepository.observeCategories(),
                isSaving,
            ) { conflict, categories, saving ->
                if (conflict == null) {
                    ExpenseConflictUiState.Resolved
                } else {
                    ExpenseConflictUiState.Deciding(
                        conflict = conflict.toUiModel(categories.associateBy(Category::id), zoneId, locale),
                        isSaving = saving,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExpenseConflictUiState.Loading,
            )

        fun keepThisDevice() = decide { conflictRepository.keepLocal(expenseId) }

        fun keepOtherDevice() = decide { conflictRepository.keepRemote(expenseId) }

        /** One decision per conflict: a double tap must not keep one version and then the other. */
        private fun decide(action: suspend () -> Unit) {
            if (isSaving.value) return
            isSaving.value = true
            viewModelScope.launch { action() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
