package com.spendlens.navigation

import androidx.annotation.StringRes
import com.spendlens.R
import com.spendlens.feature.expenses.navigation.ExpensesGraph
import com.spendlens.feature.insights.navigation.InsightsGraph
import com.spendlens.feature.settings.navigation.SettingsGraph
import kotlin.reflect.KClass

/**
 * The tabs in the bottom bar.
 *
 * Each holds the **graph** class, not the start destination. That is what makes
 * `NavDestination.hierarchy` keep the right tab selected while the user is three screens deep inside
 * it — matching only the start destination is the classic bottom-bar bug where the highlight
 * disappears on a detail screen.
 */
enum class TopLevelDestination(
    @param:StringRes val labelRes: Int,
    val graph: KClass<*>,
) {
    EXPENSES(R.string.nav_expenses, ExpensesGraph::class),
    INSIGHTS(R.string.nav_insights, InsightsGraph::class),
    SETTINGS(R.string.nav_settings, SettingsGraph::class),
}
