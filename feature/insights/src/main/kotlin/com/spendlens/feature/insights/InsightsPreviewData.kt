package com.spendlens.feature.insights

import android.content.Context
import androidx.annotation.StringRes
import com.spendlens.core.model.BudgetStatus
import com.spendlens.core.model.CategorySpend
import com.spendlens.core.model.categorySpend
import com.spendlens.core.model.formatAsMoney
import com.spendlens.core.model.monthlyTotalMinor
import com.spendlens.core.model.sample.SampleBudgets
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import kotlinx.collections.immutable.toImmutableList
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stands in for the repository until `core:data` exists.
 *
 * Takes a [Strings] lookup rather than a `Context` so the mapping stays a pure function — the
 * preview passes canned English, the running app passes real resources.
 */
internal object FakeInsights {
    fun success(
        month: YearMonth,
        zoneId: ZoneId,
        locale: Locale,
        strings: Strings,
    ): InsightsUiState {
        val totalMinor = monthlyTotalMinor(SampleExpenses.allIncludingDeleted, month, zoneId)
        if (totalMinor == 0L) return InsightsUiState.Empty

        val spend = categorySpend(
            expenses = SampleExpenses.allIncludingDeleted,
            budgets = SampleBudgets.all,
            month = month,
            zoneId = zoneId,
        )

        return InsightsUiState.Success(
            monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
            totalSpend = totalMinor.formatAsMoney(CURRENCY, locale),
            categories = spend
                .map { it.toUiModel(totalMinor, locale, strings) }
                .toImmutableList(),
        )
    }

    private fun CategorySpend.toUiModel(
        totalMinor: Long,
        locale: Locale,
        strings: Strings,
    ): CategoryInsightUiModel {
        val category = SampleCategories.byId[categoryId]
        val name = category?.name ?: categoryId
        val spentText = spentMinor.formatAsMoney(CURRENCY, locale)
        val limitText = limitMinor?.formatAsMoney(CURRENCY, locale)

        return CategoryInsightUiModel(
            categoryId = categoryId,
            name = name,
            colorIndex = category?.colorIndex ?: 0,
            spent = spentText,
            limit = limitText,
            status = status,
            fractionOfBudget = fractionOfBudget,
            shareOfTotal = if (totalMinor > 0L) spentMinor.toFloat() / totalMinor else 0f,
            stateDescription = if (limitText == null) {
                strings.withoutBudget(name, spentText)
            } else {
                strings.withBudget(name, spentText, limitText, strings.status(status))
            },
        )
    }

    private const val CURRENCY = "USD"
}

/** The strings the mapping needs, so it can run without a `Context`. */
internal interface Strings {
    fun withBudget(
        name: String,
        spent: String,
        limit: String,
        status: String,
    ): String

    fun withoutBudget(
        name: String,
        spent: String,
    ): String

    fun status(status: BudgetStatus?): String
}

internal class ResourceStrings(
    private val context: Context,
) : Strings {
    override fun withBudget(
        name: String,
        spent: String,
        limit: String,
        status: String,
    ): String = context.getString(R.string.insights_state_with_budget, name, spent, limit, status)

    override fun withoutBudget(
        name: String,
        spent: String,
    ): String = context.getString(R.string.insights_state_without_budget, name, spent)

    override fun status(status: BudgetStatus?): String = context.getString(status.labelRes())

    @StringRes
    private fun BudgetStatus?.labelRes(): Int =
        when (this) {
            BudgetStatus.UNDER -> R.string.insights_status_under
            BudgetStatus.NEAR -> R.string.insights_status_near
            BudgetStatus.OVER -> R.string.insights_status_over
            null -> R.string.insights_no_budget
        }
}

/** Canned English, so previews and screenshot tests never touch resources. */
private object PreviewStrings : Strings {
    override fun withBudget(
        name: String,
        spent: String,
        limit: String,
        status: String,
    ) = "$name, $spent of $limit spent, $status"

    override fun withoutBudget(
        name: String,
        spent: String,
    ) = "$name, $spent spent, no budget set"

    override fun status(status: BudgetStatus?) =
        when (status) {
            BudgetStatus.UNDER -> "under budget"
            BudgetStatus.NEAR -> "nearing budget"
            BudgetStatus.OVER -> "over budget"
            null -> "no budget set"
        }
}

/** Pinned to the month the fixtures live in, so the screen is never empty in a preview. */
internal object InsightsPreviewData {
    val success: InsightsUiState = FakeInsights.success(
        month = YearMonth.of(2026, 8),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        strings = PreviewStrings,
    )
}
