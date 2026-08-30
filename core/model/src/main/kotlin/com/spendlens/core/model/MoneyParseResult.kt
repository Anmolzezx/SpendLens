package com.spendlens.core.model

/**
 * The outcome of reading a user-typed amount.
 *
 * A sealed result rather than a nullable `Long`: the edit screen has to tell the user *why* their
 * input was rejected, and "not a number" and "too many decimal places" deserve different messages.
 */
sealed interface MoneyParseResult {
    data class Success(
        val amountMinor: Long,
    ) : MoneyParseResult

    data object Empty : MoneyParseResult

    data object NotANumber : MoneyParseResult

    /** More decimal places than the currency has minor units — 12.345 in USD, or 12.3 in JPY. */
    data object TooPrecise : MoneyParseResult

    data object Negative : MoneyParseResult

    /** Larger than [Long.MAX_VALUE] minor units. Absurd, but it must not silently wrap. */
    data object TooLarge : MoneyParseResult
}
