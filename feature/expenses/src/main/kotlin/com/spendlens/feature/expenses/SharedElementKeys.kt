package com.spendlens.feature.expenses

/**
 * The keys that tie a list row to the detail screen it opens.
 *
 * Defined once because a shared element only animates when both ends agree on the key, and a typo
 * silently degrades to a cross-fade — the failure nobody notices in review.
 */
internal fun merchantSharedKey(expenseId: String) = "expense-$expenseId-merchant"

internal fun amountSharedKey(expenseId: String) = "expense-$expenseId-amount"
