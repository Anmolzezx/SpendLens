package com.spendlens.feature.capture

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The live viewfinder.
 *
 * `CameraXViewfinder` from `camera-compose` rather than wrapping `PreviewView` in an `AndroidView` —
 * it is the supported Compose surface and removes the lifecycle bookkeeping that wrapper needs.
 *
 * Isolated in its own file because it is the one part of this feature that cannot run in a preview
 * or a screenshot test: it needs real camera hardware.
 */
@Composable
internal fun CameraViewfinder(
    imageCapture: ImageCapture,
    cameraProviderProvider: suspend () -> ProcessCameraProvider,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest: SurfaceRequest? by remember { mutableStateOf(null) }

    LaunchedEffect(lifecycleOwner) {
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val provider = cameraProviderProvider()
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier)
    }
}
