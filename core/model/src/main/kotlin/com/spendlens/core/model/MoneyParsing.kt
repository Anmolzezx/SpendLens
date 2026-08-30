package com.spendlens.core.model

import java.math.BigDecimal
import java.util.Currency

/**
 * Parses a user-typed amount into minor units — the inverse of [formatAsMoney].
 *
 * Accepts a single decimal separator, either `.` or `,`, so a comma-decimal locale works without the
 * caller knowing which is in use. **Grouping separators are rejected**: "1,234.56" is ambiguous
 * without knowing the locale, and a number pad does not produce them anyway.
 *
 * The precision limit comes from the currency, not a hardcoded 2 — "1200.5" is a valid USD amount and
 * an invalid JPY one.
 */
fun parseMoney(
    input: String,
    currencyCode: String,
): MoneyParseResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return MoneyParseResult.Empty

    val separators = trimmed.count { it == '.' || it == ',' }
    if (separators > 1) return MoneyParseResult.NotANumber

    val normalised = trimmed.replace(',', '.')
    val decimal = normalised.toBigDecimalOrNull() ?: return MoneyParseResult.NotANumber
    if (decimal.signum() < 0) return MoneyParseResult.Negative

    val fractionDigits = Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
    // scale() is the count of declared decimal places, so "12.30" is scale 2 even though it could
    // be written as 12.3 — which is what we want: the user typed two places, and two are allowed.
    if (decimal.scale() > fractionDigits) return MoneyParseResult.TooPrecise

    return runCatching { decimal.movePointRight(fractionDigits).longValueExact() }
        .fold(
            onSuccess = { MoneyParseResult.Success(it) },
            onFailure = { MoneyParseResult.TooLarge },
        )
}

/** Renders minor units back into an editable string — no currency symbol, no grouping. */
fun Long.toAmountInput(currencyCode: String): String {
    val fractionDigits = Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
    return BigDecimal.valueOf(this, fractionDigits).toPlainString()
}
