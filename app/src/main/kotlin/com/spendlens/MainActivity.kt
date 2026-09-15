package com.spendlens

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spendlens.core.applock.AppLockController
import com.spendlens.core.applock.AppLockState
import com.spendlens.core.applock.LockScreen
import com.spendlens.core.applock.UnlockPrompt
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.ui.SpendLensApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * `@AndroidEntryPoint` is what makes this Activity a Hilt component holder. Without it, the
 * generated `Hilt_MainActivity` superclass is never inserted, so `hiltViewModel()` finds nothing to
 * resolve a ViewModel from — and the failure is at runtime, not compile time.
 *
 * A `FragmentActivity` rather than a plain `ComponentActivity` only because the system biometric prompt
 * attaches to a fragment manager.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appLockController: AppLockController

    private lateinit var unlockPrompt: UnlockPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockPrompt = UnlockPrompt(
            activity = this,
            onSucceeded = appLockController::onAuthenticated,
            onEnded = appLockController::onAuthenticationEnded,
        )
        hideRecentsThumbnailWhenAppLockIsOn()

        setContent {
            // Follows the system for now. A theme setting would sit in core:datastore beside app lock, read
            // behind a splash-screen condition so the app never flashes the wrong theme.
            val darkTheme = isSystemInDarkTheme()

            // API 35 enforces edge-to-edge and deprecates window.statusBarColor, so this is the
            // supported way to style the system bars. The DisposableEffect re-applies it when the
            // theme flips at runtime, which a one-shot call in onCreate would miss.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightScrim,
                        DarkScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }

            SpendLensTheme(darkTheme = darkTheme) {
                val lockState by appLockController.state.collectAsStateWithLifecycle()
                val keyboard = LocalSoftwareKeyboardController.current

                Box(Modifier.fillMaxSize()) {
                    // Always composed, so navigation and half-typed input survive a lock. While locked
                    // it is removed from the accessibility tree: TalkBack must not read expenses from
                    // behind the lock screen.
                    Box(
                        if (lockState == AppLockState.Unlocked) Modifier else Modifier.clearAndSetSemantics {},
                    ) {
                        SpendLensApp()
                    }

                    when (lockState) {
                        // An opaque blank for the moment it takes to read the setting, so no expense
                        // flashes on screen before the lock appears.
                        AppLockState.Checking -> Surface(
                            Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {}

                        AppLockState.Locked -> {
                            LockScreen(onUnlockClick = unlockPrompt::show)
                            // Ask straight away, once per lock. A cancelled prompt leaves the Unlock button
                            // rather than re-opening itself in a loop.
                            LaunchedEffect(Unit) {
                                keyboard?.hide()
                                unlockPrompt.show()
                            }
                        }

                        AppLockState.Unlocked -> Unit
                    }
                }
            }
        }
    }

    /**
     * With app lock on, the recents screen shows a blank card rather than a snapshot of someone's
     * expenses. Android 13+ only; unlike `FLAG_SECURE` it does not also block the user's own screenshots.
     */
    private fun hideRecentsThumbnailWhenAppLockIsOn() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLockController.isEnabled.collect { enabled -> setRecentsScreenshotEnabled(!enabled) }
            }
        }
    }
}

/** Matches the scrims the framework draws behind 3-button navigation. */
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
