package com.spendlens.feature.expenses.detail

import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun Expense.toDetailUiModel(
    category: Category?,
    uncategorisedLabel: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): ExpenseDetailUiModel = ExpenseDetailUiModel(
    id = id,
    merchant = merchant,
    formattedAmount = amountMinor.formatAsMoney(currency, locale),
    formattedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(locale)
        .withZone(zoneId)
        .format(occurredAt),
    categoryName = category?.name ?: uncategorisedLabel,
    categoryColorIndex = category?.colorIndex ?: 0,
    note = note,
    receiptImagePath = receiptImagePath,
    syncState = syncState,
)

/** Stands in for the repository until phase 1's Room work lands. */
internal object FakeExpenseDetail {

    fun stateFor(
        expenseId: String,
        uncategorisedLabel: String,
        zoneId: ZoneId,
        locale: Locale,
    ): ExpenseDetailUiState {
        val expense = SampleExpenses.all.firstOrNull { it.id == expenseId }
            ?: return ExpenseDetailUiState.NotFound
        return ExpenseDetailUiState.Success(
            expense.toDetailUiModel(
                category = SampleCategories.byId[expense.categoryId],
                uncategorisedLabel = uncategorisedLabel,
                zoneId = zoneId,
                locale = locale,
            ),
        )
    }
}

/** Timezone and locale pinned, so previews and screenshot tests render identically anywhere. */
internal object ExpenseDetailPreviewData {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val locale: Locale = Locale.US

    /** Long merchant name, a wrapping note, a receipt, and a PENDING badge. */
    val withReceiptAndNote: ExpenseDetailUiState = state(SampleExpenses.all[12].id)

    /** No note, no receipt — the branches that must not leave empty gaps. */
    val minimal: ExpenseDetailUiState = state(SampleExpenses.withoutReceipt.id)

    private fun state(id: String) = FakeExpenseDetail.stateFor(
        expenseId = id,
        uncategorisedLabel = "Uncategorised",
        zoneId = zone,
        locale = locale,
    )
}
