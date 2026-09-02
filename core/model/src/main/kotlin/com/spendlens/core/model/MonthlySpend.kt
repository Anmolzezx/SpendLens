package com.spendlens.core.model

import java.time.YearMonth
import java.time.ZoneId

/**
 * Whether this expense falls in [month].
 *
 * **[zoneId] changes the answer**, and that is a real modelling problem rather than a quirk of this
 * function. `occurredAt` is an `Instant`, so an expense recorded at 23:00 UTC on 31 August lands in
 * September for anyone east of UTC. See the open question in DECISIONS.md — if `occurredAt` becomes
 * a `LocalDate`, this parameter disappears and the ambiguity with it.
 */
fun Expense.occurredIn(
    month: YearMonth,
    zoneId: ZoneId,
): Boolean = YearMonth.from(occurredAt.atZone(zoneId)) == month

/**
 * Spend per category for one month, tombstones excluded.
 *
 * Categories with no spend are absent rather than zero — the caller decides whether an untouched
 * budget is worth a row, and that is a presentation question.
 */
fun monthlySpendByCategory(
    expenses: List<Expense>,
    month: YearMonth,
    zoneId: ZoneId,
): Map<String, Long> =
    expenses
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { it.occurredIn(month, zoneId) }
        .groupingBy { it.categoryId }
        .fold(0L) { sum, expense -> sum + expense.amountMinor }

/** Total spend for one month, tombstones excluded. */
fun monthlyTotalMinor(
    expenses: List<Expense>,
    month: YearMonth,
    zoneId: ZoneId,
): Long =
    expenses
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { it.occurredIn(month, zoneId) }
        .sumOf { it.amountMinor }

/**
 * Joins spend to budgets, ordered by spend descending — biggest first is what someone opening an
 * insights screen is actually looking for.
 *
 * Budgeted categories with no spend are included at zero, because "you have spent nothing of your
 * £300 grocery budget" is information. Unbudgeted categories appear only if they were used.
 */
fun categorySpend(
    expenses: List<Expense>,
    budgets: List<Budget>,
    month: YearMonth,
    zoneId: ZoneId,
): List<CategorySpend> {
    val spend = monthlySpendByCategory(expenses, month, zoneId)
    val budgetByCategory = budgets.filter { it.month == month }.associateBy { it.categoryId }

    return (spend.keys + budgetByCategory.keys)
        .map { categoryId ->
            CategorySpend(
                categoryId = categoryId,
                spentMinor = spend[categoryId] ?: 0L,
                limitMinor = budgetByCategory[categoryId]?.limitMinor,
            )
        }.sortedByDescending { it.spentMinor }
}
