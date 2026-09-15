package com.spendlens.core.applock

import com.spendlens.core.common.di.ApplicationScope
import com.spendlens.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockState {
    /** The setting has not been read yet. Nothing is shown, so no expense flashes before a lock. */
    Checking,
    Locked,
    Unlocked,
}

/**
 * Decides when SpendLens is locked. No Android types, so every rule below is a plain unit test.
 *
 * - Locked on a cold start, if app lock is on.
 * - Locked when coming back after at least [LOCK_AFTER] in the background. A shorter trip — reading a
 *   code from another app — does not demand a fingerprint.
 * - **Never locks a device that cannot unlock it.** If the phone's screen lock is removed while app
 *   lock is on, a lock screen would be a wall with no door, so the app stays open.
 * - Turning app lock on does not lock immediately: whoever turned it on is right here.
 * - The PIN screen is another app, so unlocking with a PIN sends SpendLens to the background. A
 *   successful unlock forgets that trip, or a PIN typed slowly would lock the app again the moment the
 *   user got back in.
 */
@Singleton
class AppLockController
    @Inject
    constructor(
        userPreferences: UserPreferencesRepository,
        private val authenticator: DeviceAuthenticator,
        private val clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val _state = MutableStateFlow(AppLockState.Checking)
        val state: StateFlow<AppLockState> = _state.asStateFlow()

        /** Whether app lock is turned on; the app hides its recents thumbnail while it is. */
        val isEnabled: Flow<Boolean> = userPreferences.appLockEnabled

        // Lifecycle callbacks arrive on the main thread and the setting on a background one.
        private val lock = Any()
        private var enabled = false
        private var backgroundedAt: Instant? = null

        init {
            scope.launch {
                userPreferences.appLockEnabled.collect { isEnabled ->
                    synchronized(lock) {
                        val isColdStart = _state.value == AppLockState.Checking
                        enabled = isEnabled
                        _state.value = when {
                            !isEnabled -> AppLockState.Unlocked
                            isColdStart && authenticator.canAuthenticate() -> AppLockState.Locked
                            isColdStart -> AppLockState.Unlocked
                            else -> _state.value
                        }
                    }
                }
            }
        }

        fun onAppBackgrounded() = synchronized(lock) { backgroundedAt = clock.instant() }

        fun onAppForegrounded() =
            synchronized(lock) {
                val since = backgroundedAt
                backgroundedAt = null
                when {
                    !enabled -> Unit
                    !authenticator.canAuthenticate() -> _state.value = AppLockState.Unlocked
                    since != null && Duration.between(since, clock.instant()) >= LOCK_AFTER ->
                        _state.value = AppLockState.Locked
                }
            }

        fun onAuthenticated() =
            synchronized(lock) {
                // The trip to the PIN screen is not time away from the app.
                backgroundedAt = null
                _state.value = AppLockState.Unlocked
            }

        /**
         * Cancelled or failed: stay locked, and let the user try again. Unless the reason is that this
         * device can no longer authenticate at all — then locking would be permanent.
         */
        fun onAuthenticationEnded() =
            synchronized(lock) {
                if (!authenticator.canAuthenticate()) _state.value = AppLockState.Unlocked
            }

        companion object {
            val LOCK_AFTER: Duration = Duration.ofSeconds(30)
        }
    }
