package com.spendlens.feature.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Camera permission has three states, not two.
 *
 * The third — denied so firmly that the system will no longer show the dialog — is the one apps
 * usually get wrong: they keep calling `launch()`, the dialog never appears, and the screen sits
 * there doing nothing. Detecting it means the UI can send the user to Settings instead.
 */
enum class CameraPermissionState {
    Granted,

    /** Not granted, but the system will still show the dialog. */
    Deniable,

    /**
     * Permanently denied — "Don't allow" twice, or "Never ask again".
     *
     * Android has no direct API for this. It is inferred: after a denial,
     * `shouldShowRequestPermissionRationale` returns false only when the dialog will not appear
     * again. Before the *first* request it also returns false, which is why [hasAsked] is tracked.
     */
    PermanentlyDenied,
}

@Composable
internal fun rememberCameraPermission(): CameraPermissionController {
    val context = LocalContext.current
    var state by remember { mutableStateOf(context.currentCameraPermission(hasAsked = false)) }
    var hasAsked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAsked = true
        state = context.currentCameraPermission(hasAsked = true)
    }

    return remember(state) {
        CameraPermissionController(
            state = state,
            request = {
                if (state == CameraPermissionState.Deniable) {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            },
            refresh = { state = context.currentCameraPermission(hasAsked) },
        )
    }
}

internal class CameraPermissionController(
    val state: CameraPermissionState,
    val request: () -> Unit,
    /** Call on resume: the user may have granted the permission in Settings and come back. */
    val refresh: () -> Unit,
)

private fun Context.currentCameraPermission(hasAsked: Boolean): CameraPermissionState {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return CameraPermissionState.Granted

    val activity = findActivity() ?: return CameraPermissionState.Deniable
    val canAskAgain = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    // Before the first ask, rationale is also false — which is why a bare check here would
    // misreport a fresh install as permanently denied.
    return when {
        canAskAgain -> CameraPermissionState.Deniable
        hasAsked -> CameraPermissionState.PermanentlyDenied
        else -> CameraPermissionState.Deniable
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
