package com.spendlens.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.SpendLensTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDeviceSecurity()
        onPauseOrDispose {}
    }

    SettingsContent(
        uiState = uiState,
        onAppLockChange = viewModel::setAppLockEnabled,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onAppLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (uiState is SettingsUiState.Success) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (uiState.canUseAppLock) {
                                    R.string.settings_app_lock_description
                                } else {
                                    R.string.settings_app_lock_unavailable
                                },
                            ),
                        )
                    },
                    // The Switch has no click handler of its own: the whole row is the control, so it
                    // is one large touch target and TalkBack reads label, description and state together.
                    trailingContent = {
                        Switch(
                            checked = uiState.appLockEnabled,
                            onCheckedChange = null,
                            enabled = uiState.canChangeAppLock,
                        )
                    },
                    modifier = Modifier.toggleable(
                        value = uiState.appLockEnabled,
                        enabled = uiState.canChangeAppLock,
                        role = Role.Switch,
                        onValueChange = onAppLockChange,
                    ),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsPreview() {
    SpendLensTheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState.Success(appLockEnabled = true, canUseAppLock = true),
                onAppLockChange = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsNoScreenLockPreview() {
    SpendLensTheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState.Success(appLockEnabled = false, canUseAppLock = false),
                onAppLockChange = {},
            )
        }
    }
}
