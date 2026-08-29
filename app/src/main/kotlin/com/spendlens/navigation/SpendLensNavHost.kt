package com.spendlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.spendlens.feature.expenses.navigation.ExpensesGraph
import com.spendlens.feature.expenses.navigation.expensesGraph

/**
 * The one place that knows about every feature.
 *
 * Features expose `NavGraphBuilder` extensions and lambdas; this is where cross-feature intent gets
 * resolved into an actual destination. When `feature:capture` exists, `onNavigateToCapture` becomes
 * `navController.navigateToCapture()` and `feature:expenses` still will not know it exists.
 */
@Composable
fun SpendLensNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ExpensesGraph,
        modifier = modifier,
    ) {
        expensesGraph(
            // TODO(phase 1): route to the edit screen once feature:expenses has one.
            onNavigateToEdit = {},
            // TODO(phase 2): navController.navigateToCapture()
            onNavigateToCapture = {},
            navController = navController,
        )
    }
}
