package com.spendlens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.spendlens.feature.capture.navigation.CaptureRoute
import com.spendlens.feature.capture.navigation.captureScreen
import com.spendlens.feature.capture.navigation.navigateToCapture
import com.spendlens.feature.expenses.navigation.ExpenseEditRoute
import com.spendlens.feature.expenses.navigation.ExpensesGraph
import com.spendlens.feature.expenses.navigation.expensesGraph
import com.spendlens.feature.insights.navigation.insightsGraph
import com.spendlens.feature.settings.navigation.settingsGraph

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
            onNavigateToCapture = { navController.navigateToCapture() },
            navController = navController,
        )
        captureScreen(
            // The cross-feature join: capture produces a ParsedReceipt, expenses consumes it, and
            // neither module imports the other — this is the only place that knows both exist.
            onCaptured = { receipt, imagePath ->
                navController.navigate(
                    ExpenseEditRoute(
                        merchant = receipt.merchant,
                        amountMinor = receipt.totalMinor,
                        // The parser already returns a LocalDate, so it now passes straight through —
                        // no timezone round-trip that could shift the printed date by a day.
                        occurredOnEpochDay = receipt.date?.toEpochDay(),
                        receiptImagePath = imagePath,
                    ),
                ) {
                    // Do not leave the viewfinder behind the review form; back should return to
                    // the list, not to a camera pointing at a receipt already captured.
                    popUpTo<CaptureRoute> { inclusive = true }
                }
            },
            onEnterManually = {
                navController.navigate(ExpenseEditRoute()) {
                    popUpTo<CaptureRoute> { inclusive = true }
                }
            },
            onCancel = { navController.popBackStack() },
        )
        insightsGraph()
        settingsGraph()
    }
}
