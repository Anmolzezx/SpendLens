package com.spendlens.feature.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.core.ocr.ParsedReceipt

/**
 * Stateful half of the capture screen.
 *
 * @param onCaptured carries the parsed fields upward. `feature:capture` does not know the expense
 *   edit screen exists — `:app` wires this to a route, which is the same rule that keeps every other
 *   feature independent.
 */
@Composable
fun CaptureScreen(
    onCaptured: (ParsedReceipt, imagePath: String) -> Unit,
    onEnterManually: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()
    val imageCapture = remember { ImageCapture.Builder().build() }

    // The user may grant the permission in Settings and come straight back; without re-checking on
    // resume the screen would still be showing the "blocked" message.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState) {
        val recognized = uiState as? CaptureUiState.Recognized ?: return@LaunchedEffect
        onCaptured(recognized.receipt, recognized.imagePath)
    }

    CaptureContent(
        uiState = uiState,
        permission = permission.state,
        onRequestPermission = permission.request,
        onOpenSettings = { context.openAppSettings() },
        onCapture = {
            viewModel.onCaptureStarted()
            val file = viewModel.newImageFile()
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        viewModel.onImageCaptured(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        viewModel.onCaptureFailed()
                    }
                },
            )
        },
        onRetry = viewModel::retry,
        onEnterManually = onEnterManually,
        onCancel = onCancel,
        modifier = modifier,
        viewfinder = { viewfinderModifier ->
            CameraViewfinder(
                imageCapture = imageCapture,
                cameraProviderProvider = { ProcessCameraProvider.getInstance(context).await(context) },
                modifier = viewfinderModifier,
            )
        },
    )
}

private fun android.content.Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
