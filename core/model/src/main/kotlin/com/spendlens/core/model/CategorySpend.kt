package com.spendlens.core.model

/** What one category cost in a given month, against its budget if it has one. */
data class CategorySpend(
    val categoryId: String,
    val spentMinor: Long,
    val limitMinor: Long?,
) {
    val status: BudgetStatus? = limitMinor?.let { BudgetStatus.of(spentMinor, it) }

    /** 0f..1f+ — the caller coerces for display. Null when the category has no budget. */
    val fractionOfBudget: Float? =
        limitMinor?.takeIf { it > 0L }?.let { spentMinor.toFloat() / it }
}
