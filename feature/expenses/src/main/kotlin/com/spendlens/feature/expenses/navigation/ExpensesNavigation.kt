package com.spendlens.feature.expenses.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.spendlens.feature.expenses.detail.ExpenseDetailScreen
import com.spendlens.feature.expenses.edit.ExpenseEditScreen
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
data class ExpenseDetailRoute(
    val expenseId: String,
)

/** [expenseId] is null when creating a new expense — the same screen serves both. */
@Serializable
data class ExpenseEditRoute(
    val expenseId: String? = null,
)

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
    onNavigateToCapture: () -> Unit,
    navController: NavController,
) {
    navigation<ExpensesGraph>(startDestination = ExpenseListRoute) {
        composable<ExpenseListRoute> {
            ExpenseListScreen(
                onExpenseClick = { id -> navController.navigate(ExpenseDetailRoute(id)) },
                onAddExpenseClick = { navController.navigate(ExpenseEditRoute()) },
                // The one genuinely cross-feature edge, and the only one handed upward.
                onScanReceiptClick = onNavigateToCapture,
            )
        }
        composable<ExpenseDetailRoute> {
            // The route arguments are read by the ViewModel from its SavedStateHandle now, so the
            // graph no longer threads them through the composable signature.
            ExpenseDetailScreen(
                onBack = { navController.popBackStack() },
                onEditClick = { id -> navController.navigate(ExpenseEditRoute(id)) },
            )
        }
        composable<ExpenseEditRoute> {
            ExpenseEditScreen(
                // Both land back where the user came from. Once saving is real, the list will be
                // showing the new row already, because it observes the database rather than a result.
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
