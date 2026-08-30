package com.spendlens.feature.expenses.edit

import com.spendlens.core.model.sample.SampleCategories
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/** Fixtures with a pinned date, so previews and screenshot baselines stay deterministic. */
internal object ExpenseEditPreviewData {
    private val fixedDate = Instant.parse("2026-08-26T18:42:00Z").toEpochMilli()

    val blank = state(
        isNewExpense = true,
        merchant = "",
        amount = "",
        categoryId = null,
        note = "",
    )

    val filled = state(
        isNewExpense = false,
        merchant = "Trader Joe's",
        amount = "42.87",
        categoryId = SampleCategories.groceries.id,
        note = "Weekly shop",
    )

    /** Save attempted with an unparseable amount and nothing else filled in. */
    val withErrors = state(
        isNewExpense = true,
        merchant = "",
        amount = "12.345",
        categoryId = null,
        note = "",
        showErrors = true,
    )

    private fun state(
        isNewExpense: Boolean,
        merchant: String,
        amount: String,
        categoryId: String?,
        note: String,
        showErrors: Boolean = false,
    ) = ExpenseEditUiState(
        isNewExpense = isNewExpense,
        merchant = merchant,
        amount = amount,
        currency = "USD",
        categoryId = categoryId,
        note = note,
        occurredAtMillis = fixedDate,
        categories = SampleCategories.all.toImmutableList(),
        showErrors = showErrors,
    )
}
