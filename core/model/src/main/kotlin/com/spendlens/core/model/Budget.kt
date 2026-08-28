package com.spendlens.core.model

import java.time.YearMonth

/** A per-category spending limit for one calendar month. */
data class Budget(
    val categoryId: String,
    val limitMinor: Long,
    val currency: String,
    val month: YearMonth,
)

/** How a category is tracking against its [Budget]. The UI maps this to a colour and a label. */
enum class BudgetStatus {
    UNDER,

    /** At or above [NEAR_THRESHOLD] of the limit, but not yet over it. */
    NEAR,

    OVER,
    ;

    companion object {
        const val NEAR_THRESHOLD: Float = 0.8f

        fun of(spentMinor: Long, limitMinor: Long): BudgetStatus = when {
            limitMinor <= 0L -> UNDER
            spentMinor > limitMinor -> OVER
            spentMinor.toFloat() / limitMinor >= NEAR_THRESHOLD -> NEAR
            else -> UNDER
        }
    }
}
