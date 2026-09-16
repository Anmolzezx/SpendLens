package com.spendlens.core.applock

import android.content.Context
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether this device can prove who is holding it. Behind an interface so lock rules are testable. */
interface DeviceAuthenticator {
    fun canAuthenticate(): Boolean
}

/**
 * Biometrics **or** the screen lock (PIN, pattern, password).
 *
 * `BIOMETRIC_WEAK`, not `STRONG`: this lock guards a screen, not a cryptographic key, and "weak"
 * includes most face unlock. `STRONG` combined with the device credential is also unsupported on
 * Android 9 and 10, which this app still runs on.
 */
internal const val UNLOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal class BiometricDeviceAuthenticator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DeviceAuthenticator {
        override fun canAuthenticate(): Boolean =
            BiometricManager.from(context).canAuthenticate(UNLOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
