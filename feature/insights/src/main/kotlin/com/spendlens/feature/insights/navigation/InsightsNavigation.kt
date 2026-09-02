package com.spendlens.feature.insights.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.spendlens.feature.insights.InsightsScreen
import kotlinx.serialization.Serializable

@Serializable
data object InsightsGraph

@Serializable
data object InsightsRoute

fun NavController.navigateToInsights(navOptions: NavOptions? = null) =
    navigate(route = InsightsGraph, navOptions = navOptions)

/**
 * A single-destination graph rather than a bare `composable`.
 *
 * The nesting is what lets the bottom bar treat "Insights" as one tab: `NavDestination.hierarchy`
 * matches the graph, so a future detail screen inside it keeps the tab selected.
 */
fun NavGraphBuilder.insightsGraph() {
    navigation<InsightsGraph>(startDestination = InsightsRoute) {
        composable<InsightsRoute> { InsightsScreen() }
    }
}
