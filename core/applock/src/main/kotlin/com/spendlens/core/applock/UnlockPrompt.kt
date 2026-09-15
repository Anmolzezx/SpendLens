package com.spendlens.core.applock

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The system's fingerprint / face / PIN prompt.
 *
 * Construct it in the activity's `onCreate`: `BiometricPrompt` registers with the activity's fragment
 * manager, which is not safe once the activity has saved its state.
 */
class UnlockPrompt(
    private val activity: FragmentActivity,
    onSucceeded: () -> Unit,
    onEnded: () -> Unit,
) {
    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSucceeded()

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) = onEnded()

            // onAuthenticationFailed is one unrecognised finger. The prompt stays open to try again, so
            // nothing ends here.
        },
    )

    fun show() {
        prompt.authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.app_lock_prompt_title))
                .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
                // No negative button: with the device credential allowed, the prompt offers "Use PIN"
                // itself, and setting one as well throws.
                .setAllowedAuthenticators(UNLOCK_AUTHENTICATORS)
                .build(),
        )
    }
}
