package com.spendlens.core.model

import java.time.YearMonth

/**
 * Whether this expense falls in [month].
 *
 * This used to take a `ZoneId`, because the answer depended on one: `occurredAt` was an instant, and
 * an expense at 23:00 UTC on 31 August belonged to September east of UTC. Making the date a
 * `LocalDate` removed the parameter and the ambiguity together.
 */
fun Expense.occurredIn(month: YearMonth): Boolean = YearMonth.from(occurredOn) == month

/**
 * Spend per category for one month, tombstones excluded.
 *
 * Categories with no spend are absent rather than zero — the caller decides whether an untouched
 * budget is worth a row, and that is a presentation question.
 */
fun monthlySpendByCategory(
    expenses: List<Expense>,
    month: YearMonth,
): Map<String, Long> =
    expenses
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { it.occurredIn(month) }
        .groupingBy { it.categoryId }
        .fold(0L) { sum, expense -> sum + expense.amountMinor }

/** Total spend for one month, tombstones excluded. */
fun monthlyTotalMinor(
    expenses: List<Expense>,
    month: YearMonth,
): Long =
    expenses
        .asSequence()
        .filterNot { it.isDeleted }
        .filter { it.occurredIn(month) }
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
): List<CategorySpend> {
    val spend = monthlySpendByCategory(expenses, month)
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

/** One month's total, for a spending trend. */
data class MonthTotal(
    val month: YearMonth,
    val totalMinor: Long,
)

/** [count] months ending with [endMonth], oldest first. */
fun trailingMonths(
    endMonth: YearMonth,
    count: Int,
): List<YearMonth> {
    require(count > 0) { "A trend needs at least one month" }
    return (count - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
}

/**
 * Totals for exactly [months], in that order, tombstones excluded.
 *
 * A month with no spending is included as zero rather than dropped: a gap in a trend is information,
 * and a chart that silently skips it would compare August to June as though they were adjacent.
 */
fun monthlyTotals(
    expenses: List<Expense>,
    months: List<YearMonth>,
): List<MonthTotal> {
    val byMonth = expenses
        .asSequence()
        .filterNot { it.isDeleted }
        .groupingBy { YearMonth.from(it.occurredOn) }
        .fold(0L) { sum, expense -> sum + expense.amountMinor }

    return months.map { month -> MonthTotal(month, byMonth[month] ?: 0L) }
}
