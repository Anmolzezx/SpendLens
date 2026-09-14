package com.spendlens.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.component.EmptyState
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme

/**
 * Everything the capture screen shows *except* the live viewfinder.
 *
 * The viewfinder needs a real camera, so it is passed in as [viewfinder] rather than built here.
 * That keeps every other state — the two permission screens, processing, and both failures —
 * renderable in a preview and in a screenshot test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureContent(
    uiState: CaptureUiState,
    permission: CameraPermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCapture: () -> Unit,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable (Modifier) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.capture_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                permission == CameraPermissionState.PermanentlyDenied -> EmptyState(
                    title = stringResource(R.string.capture_permission_denied_title),
                    description = stringResource(R.string.capture_permission_denied_description),
                    actionLabel = stringResource(R.string.capture_permission_settings),
                    onAction = onOpenSettings,
                )

                permission == CameraPermissionState.Deniable -> EmptyState(
                    title = stringResource(R.string.capture_permission_title),
                    description = stringResource(R.string.capture_permission_description),
                    actionLabel = stringResource(R.string.capture_permission_grant),
                    onAction = onRequestPermission,
                )

                uiState is CaptureUiState.Failed -> FailureState(
                    failure = uiState.reason,
                    onRetry = onRetry,
                    onEnterManually = onEnterManually,
                )

                uiState is CaptureUiState.Processing -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.Large),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.capture_processing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Box(Modifier.fillMaxSize()) {
                    viewfinder(Modifier.fillMaxSize())
                    Button(
                        onClick = onCapture,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Spacing.ExtraLarge)
                            .heightIn(min = Spacing.MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.capture_shutter))
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureState(
    failure: CaptureFailure,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = stringResource(
                when (failure) {
                    CaptureFailure.CaptureFailed -> R.string.capture_failed_title
                    CaptureFailure.RecognitionFailed -> R.string.capture_recognition_failed_title
                },
            ),
            description = stringResource(
                when (failure) {
                    CaptureFailure.CaptureFailed -> R.string.capture_failed_description
                    CaptureFailure.RecognitionFailed -> R.string.capture_recognition_failed_description
                },
            ),
            actionLabel = stringResource(R.string.capture_retry),
            onAction = onRetry,
        )
        // Always offer the way out. A camera that cannot focus must not block adding an expense.
        TextButton(onClick = onEnterManually) {
            Text(stringResource(R.string.capture_enter_manually))
        }
    }
}

@ThemePreviews
@Composable
private fun CapturePermissionRequestPreview() {
    SpendLensTheme {
        Surface {
            CapturePreviewHost(permission = CameraPermissionState.Deniable)
        }
    }
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun CapturePermanentlyDeniedPreview() {
    SpendLensTheme {
        Surface {
            CapturePreviewHost(permission = CameraPermissionState.PermanentlyDenied)
        }
    }
}

@ThemePreviews
@Composable
private fun CaptureProcessingPreview() {
    SpendLensTheme {
        Surface {
            CapturePreviewHost(
                permission = CameraPermissionState.Granted,
                uiState = CaptureUiState.Processing,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun CaptureRecognitionFailedPreview() {
    SpendLensTheme {
        Surface {
            CapturePreviewHost(
                permission = CameraPermissionState.Granted,
                uiState = CaptureUiState.Failed(CaptureFailure.RecognitionFailed),
            )
        }
    }
}

@Composable
private fun CapturePreviewHost(
    permission: CameraPermissionState,
    uiState: CaptureUiState = CaptureUiState.Ready,
) {
    CaptureContent(
        uiState = uiState,
        permission = permission,
        onRequestPermission = {},
        onOpenSettings = {},
        onCapture = {},
        onRetry = {},
        onEnterManually = {},
        onCancel = {},
    )
}
