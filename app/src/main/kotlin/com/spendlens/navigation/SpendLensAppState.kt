package com.spendlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.spendlens.feature.expenses.navigation.navigateToExpenses
import com.spendlens.feature.insights.navigation.navigateToInsights
import com.spendlens.feature.settings.navigation.navigateToSettings

@Stable
class SpendLensAppState(
    val navController: NavHostController,
) {
    private val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    /**
     * `hierarchy` walks up from the current destination through its parent graphs, so the Expenses
     * tab stays selected on the detail and edit screens rather than deselecting the moment the user
     * navigates anywhere.
     */
    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            val destination = currentDestination
            return TopLevelDestination.entries.firstOrNull { topLevel ->
                destination?.hierarchy?.any { it.hasRoute(topLevel.graph) } == true
            }
        }

    fun navigateTo(destination: TopLevelDestination) {
        val options = navOptions {
            // Standard bottom-nav semantics: pop back to the graph root so the back stack does not
            // accumulate one entry per tab switch, but save and restore each tab's own stack so
            // returning to a tab returns to where the user left it.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.EXPENSES -> navController.navigateToExpenses(options)
            TopLevelDestination.INSIGHTS -> navController.navigateToInsights(options)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }
}

@Composable
fun rememberSpendLensAppState(navController: NavHostController = rememberNavController()): SpendLensAppState =
    remember(navController) { SpendLensAppState(navController) }
