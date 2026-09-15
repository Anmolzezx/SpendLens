package com.spendlens.feature.expenses.list

import androidx.compose.runtime.Immutable
import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.model.formatAsMoney
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What one row needs, and nothing more.
 *
 * Amounts and dates arrive pre-formatted rather than as `Long` and `Instant`. Formatting is
 * locale- and timezone-dependent, and doing it here — outside the composable — means it happens once
 * per state change instead of on every recomposition, and can be asserted in a plain JVM test with no
 * Compose runtime involved.
 */
@Immutable
data class ExpenseUiModel(
    val id: String,
    val merchant: String,
    val formattedAmount: String,
    val formattedDate: String,
    /** Null when the category is unknown; the composable supplies the fallback label. */
    val categoryName: String?,
    val categoryColorIndex: Int,
    val hasReceipt: Boolean,
    val syncState: SyncState,
)

/**
 * @param locale is explicit so previews and screenshot tests can pin it. There is no timezone
 *   parameter: the date is a calendar date, and formatting one needs no zone.
 */
internal fun Expense.toUiModel(
    category: Category?,
    locale: Locale = Locale.getDefault(),
): ExpenseUiModel =
    ExpenseUiModel(
        id = id,
        merchant = merchant,
        formattedAmount = amountMinor.formatAsMoney(currency, locale),
        formattedDate = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(occurredOn),
        categoryName = category?.name,
        categoryColorIndex = category?.colorIndex ?: 0,
        hasReceipt = receiptImagePath != null,
        syncState = syncState,
    )
