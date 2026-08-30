package com.spendlens.feature.expenses.edit

import androidx.compose.runtime.Immutable
import com.spendlens.core.model.Category
import com.spendlens.core.model.MoneyParseResult
import com.spendlens.core.model.parseMoney
import kotlinx.collections.immutable.ImmutableList

/** Why a field is invalid. The composable maps these to strings; the state holds no `Context`. */
enum class FieldError {
    REQUIRED,
    NOT_A_NUMBER,
    TOO_PRECISE,
    NEGATIVE,
    TOO_LARGE,
}

/**
 * Form state for creating or editing an expense.
 *
 * Errors are computed from the current input rather than stored, so they cannot go stale — but
 * [showErrors] gates whether they are *displayed*. Flagging "required" on a field the user has not
 * reached yet is hostile; the flag flips on the first save attempt.
 */
@Immutable
data class ExpenseEditUiState(
    val isNewExpense: Boolean,
    val merchant: String,
    val amount: String,
    val currency: String,
    val categoryId: String?,
    val note: String,
    val occurredAtMillis: Long,
    val categories: ImmutableList<Category>,
    val showErrors: Boolean,
) {
    val merchantError: FieldError? =
        FieldError.REQUIRED.takeIf { merchant.isBlank() }

    val amountError: FieldError? =
        when (parseMoney(amount, currency)) {
            is MoneyParseResult.Success -> null
            MoneyParseResult.Empty -> FieldError.REQUIRED
            MoneyParseResult.NotANumber -> FieldError.NOT_A_NUMBER
            MoneyParseResult.TooPrecise -> FieldError.TOO_PRECISE
            MoneyParseResult.Negative -> FieldError.NEGATIVE
            MoneyParseResult.TooLarge -> FieldError.TOO_LARGE
        }

    val categoryError: FieldError? =
        FieldError.REQUIRED.takeIf { categoryId == null }

    /**
     * Save is always tappable — a disabled button that never says why is a dead end. Tapping with
     * invalid input reveals the errors instead.
     */
    val isValid: Boolean =
        merchantError == null && amountError == null && categoryError == null
}
