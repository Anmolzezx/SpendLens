package com.spendlens.feature.expenses.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import com.spendlens.core.model.toAmountInput
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/**
 * Stateful half of the edit screen.
 *
 * Form state is held here with `rememberSaveable`, so it survives rotation and process death — the
 * same guarantee a `SavedStateHandle`-backed ViewModel will give once the data layer lands, which is
 * why swapping this body later changes nothing in [ExpenseEditContent].
 *
 * @param expenseId null when creating a new expense.
 */
@Composable
fun ExpenseEditScreen(
    expenseId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val existing = expenseId?.let { id -> SampleExpenses.all.firstOrNull { it.id == id } }

    var merchant by rememberSaveable(expenseId) { mutableStateOf(existing?.merchant.orEmpty()) }
    var amount by rememberSaveable(expenseId) {
        mutableStateOf(existing?.let { it.amountMinor.toAmountInput(it.currency) }.orEmpty())
    }
    var categoryId by rememberSaveable(expenseId) { mutableStateOf(existing?.categoryId) }
    var note by rememberSaveable(expenseId) { mutableStateOf(existing?.note.orEmpty()) }
    var occurredAtMillis by rememberSaveable(expenseId) {
        mutableStateOf(existing?.occurredAt?.toEpochMilli() ?: Instant.now().toEpochMilli())
    }
    var showErrors by rememberSaveable(expenseId) { mutableStateOf(false) }

    val uiState = ExpenseEditUiState(
        isNewExpense = existing == null,
        merchant = merchant,
        amount = amount,
        currency = existing?.currency ?: DEFAULT_CURRENCY,
        categoryId = categoryId,
        note = note,
        occurredAtMillis = occurredAtMillis,
        categories = SampleCategories.all.toImmutableList(),
        showErrors = showErrors,
    )

    ExpenseEditContent(
        uiState = uiState,
        onMerchantChange = { merchant = it },
        onAmountChange = { amount = it },
        onCategoryChange = { categoryId = it },
        onNoteChange = { note = it },
        onDateChange = { occurredAtMillis = it },
        onSave = {
            if (uiState.isValid) {
                // TODO(phase 1): persist through the repository once core:data exists.
                onSaved()
            } else {
                showErrors = true
            }
        },
        onCancel = onCancel,
        modifier = modifier,
    )
}

/** v1 is single-currency (§2 puts multi-currency FX out of scope). */
private const val DEFAULT_CURRENCY = "USD"
