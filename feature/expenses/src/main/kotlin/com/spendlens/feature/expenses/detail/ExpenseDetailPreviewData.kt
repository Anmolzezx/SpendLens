package com.spendlens.feature.expenses.detail

import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [category] may be null, and [ExpenseDetailUiModel.categoryName] stays null when it is. Resolving
 * that to "Uncategorised" needs a string resource, and a `Context` has no business in a mapper the
 * ViewModel calls — the composable supplies the fallback.
 */
internal fun Expense.toDetailUiModel(
    category: Category?,
    locale: Locale = Locale.getDefault(),
    resolveReceipt: (String) -> File?,
) = ExpenseDetailUiModel(
    id = id,
    merchant = merchant,
    formattedAmount = amountMinor.formatAsMoney(currency, locale),
    // A calendar date needs no timezone to format.
    formattedDate = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .format(occurredOn),
    categoryName = category?.name,
    categoryColorIndex = category?.colorIndex ?: 0,
    note = note,
    receiptImage = receiptImagePath?.let(resolveReceipt),
    syncState = syncState,
)

/** Locale pinned, so previews and screenshot baselines render identically anywhere. */
internal object ExpenseDetailPreviewData {
    private val locale: Locale = Locale.US

    /** Long merchant name, a wrapping note, a receipt, and a PENDING badge. */
    val withReceiptAndNote: ExpenseDetailUiState = state(SampleExpenses.all[12].id)

    /** No note, no receipt — the branches that must not leave empty gaps. */
    val minimal: ExpenseDetailUiState = state(SampleExpenses.withoutReceipt.id)

    private fun state(id: String): ExpenseDetailUiState {
        val expense = SampleExpenses.all.first { it.id == id }
        return ExpenseDetailUiState.Success(
            expense.toDetailUiModel(
                category = SampleCategories.byId[expense.categoryId],
                locale = locale,
                // Sample paths point nowhere, so previews render the "unavailable" fallback — which
                // is the state worth checking in light and dark anyway.
                resolveReceipt = ::File,
            ),
        )
    }
}
