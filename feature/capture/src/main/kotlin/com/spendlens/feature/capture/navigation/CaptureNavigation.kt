package com.spendlens.feature.capture.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.spendlens.core.ocr.ParsedReceipt
import com.spendlens.feature.capture.CaptureScreen

fun NavController.navigateToCapture(navOptions: NavOptions? = null) =
    navigate(route = CaptureRoute, navOptions = navOptions)

/**
 * @param onCaptured handed upward rather than resolved here — this feature must not know that an
 *   expense edit screen exists.
 */
fun NavGraphBuilder.captureScreen(
    onCaptured: (ParsedReceipt, imagePath: String) -> Unit,
    onEnterManually: () -> Unit,
    onCancel: () -> Unit,
) {
    composable<CaptureRoute> {
        CaptureScreen(
            onCaptured = onCaptured,
            onEnterManually = onEnterManually,
            onCancel = onCancel,
        )
    }
}
