package com.spendlens.feature.expenses.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.spendlens.feature.expenses.detail.ExpenseDetailScreen
import com.spendlens.feature.expenses.list.ExpenseListScreen
import kotlinx.serialization.Serializable

// Routes are @Serializable types, not "detail/{id}" strings. A typo is a compile error, arguments
// keep their types, and there is no manual encode/decode step to get wrong.

/** Nested graph, so `:app` can treat the whole expenses section as one destination. */
@Serializable
data object ExpensesGraph

@Serializable
data object ExpenseListRoute

@Serializable
data class ExpenseDetailRoute(val expenseId: String)

fun NavController.navigateToExpenses(navOptions: NavOptions? = null) =
    navigate(route = ExpensesGraph, navOptions = navOptions)

/**
 * The feature's graph.
 *
 * Every parameter is a lambda, and that is the load-bearing detail. `feature:expenses` cannot import
 * `feature:capture` — the §4 dependency rule forbids it — so it does not navigate there. It reports
 * that the user asked to scan a receipt, and `:app`, the only module that knows both features,
 * decides what that means. Navigation *within* the graph stays here, where it belongs.
 */
fun NavGraphBuilder.expensesGraph(
    onNavigateToEdit: (String?) -> Unit,
    onNavigateToCapture: () -> Unit,
    navController: NavController,
) {
    navigation<ExpensesGraph>(startDestination = ExpenseListRoute) {
        composable<ExpenseListRoute> {
            ExpenseListScreen(
                onExpenseClick = { id -> navController.navigate(ExpenseDetailRoute(id)) },
                onAddExpenseClick = onNavigateToCapture,
            )
        }
        composable<ExpenseDetailRoute> { backStackEntry ->
            // Typed extraction — no string keys, no null handling, no manual casting.
            val route: ExpenseDetailRoute = backStackEntry.toRoute()
            ExpenseDetailScreen(
                expenseId = route.expenseId,
                onBack = { navController.popBackStack() },
                onEditClick = onNavigateToEdit,
                onDeleteClick = { navController.popBackStack() },
            )
        }
    }
}
