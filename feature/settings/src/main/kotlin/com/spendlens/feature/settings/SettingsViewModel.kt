package com.spendlens.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.core.applock.DeviceAuthenticator
import com.spendlens.core.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(
        /** The saved setting, shown as it is — even while it cannot take effect. */
        val appLockEnabled: Boolean,
        /** False on a device with no screen lock, where app lock could never be unlocked. */
        val canUseAppLock: Boolean,
    ) : SettingsUiState {
        /**
         * Turning app lock on needs a screen lock to unlock it with; turning it off never needs anything.
         * Found on the emulator: disabling the switch outright left a saved "on" that nobody could turn
         * off, which would quietly lock the app again the day a screen lock was set up.
         */
        val canChangeAppLock: Boolean get() = canUseAppLock || appLockEnabled
    }
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userPreferences: UserPreferencesRepository,
        private val authenticator: DeviceAuthenticator,
    ) : ViewModel() {
        private val canUseAppLock = MutableStateFlow(authenticator.canAuthenticate())

        val uiState: StateFlow<SettingsUiState> =
            combine(userPreferences.appLockEnabled, canUseAppLock) { enabled, canUse ->
                SettingsUiState.Success(appLockEnabled = enabled, canUseAppLock = canUse)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState.Loading,
            )

        /** Called each time the screen resumes: the user may have just set up a screen lock and come back. */
        fun refreshDeviceSecurity() {
            canUseAppLock.value = authenticator.canAuthenticate()
        }

        fun setAppLockEnabled(enabled: Boolean) {
            viewModelScope.launch { userPreferences.setAppLockEnabled(enabled) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
