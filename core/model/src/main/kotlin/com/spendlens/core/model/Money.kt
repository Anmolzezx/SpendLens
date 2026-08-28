package com.spendlens.core.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats a minor-unit amount for display, e.g. `1234L` with `"USD"` becomes `"$12.34"`.
 *
 * The number of minor units per major unit comes from [Currency.getDefaultFractionDigits] rather than
 * a hardcoded `/ 100`: it is 2 for USD and INR, but 0 for JPY and 3 for KWD. Dividing by 100 would
 * silently render ¥1234 as ¥12.34.
 *
 * [locale] is an explicit parameter rather than an implicit [Locale.getDefault] read so that tests can
 * pin it. Currency formatting is locale-dependent — grouping separators, symbol placement and even the
 * decimal separator differ — and a formatter test that reads the ambient default passes on a developer
 * machine and fails on a CI runner with a different one.
 */
fun Long.formatAsMoney(
    currencyCode: String,
    locale: Locale = Locale.getDefault(),
): String {
    val currency = Currency.getInstance(currencyCode)
    val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    val format = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    // valueOf(unscaled, scale) builds the decimal exactly: (1234, 2) is 12.34, with no
    // binary floating point anywhere in the path.
    return format.format(BigDecimal.valueOf(this, fractionDigits))
}

/** Sum of [Expense.amountMinor] across [expenses]. Assumes a single currency, which v1 guarantees. */
fun totalMinor(expenses: List<Expense>): Long = expenses.sumOf { it.amountMinor }
