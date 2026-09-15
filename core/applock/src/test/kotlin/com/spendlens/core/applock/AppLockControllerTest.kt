package com.spendlens.core.applock

import com.spendlens.core.testing.repository.TestUserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {
    private val clock = MutableClock(Instant.parse("2026-09-16T08:00:00Z"))
    private val authenticator = FakeDeviceAuthenticator()

    // -- cold start -----------------------------------------------------------------------------------

    @Test
    fun `starts locked when app lock is on`() =
        runTest {
            assertEquals(AppLockState.Locked, controller(appLockEnabled = true).state.value)
        }

    @Test
    fun `starts unlocked when app lock is off`() =
        runTest {
            assertEquals(AppLockState.Unlocked, controller(appLockEnabled = false).state.value)
        }

    /** Screen lock removed while app lock was on: a lock screen now would be a wall with no door. */
    @Test
    fun `never locks a device that cannot unlock it`() =
        runTest {
            authenticator.canAuthenticate = false

            assertEquals(AppLockState.Unlocked, controller(appLockEnabled = true).state.value)
        }

    // -- background and back --------------------------------------------------------------------------

    @Test
    fun `a short trip away does not lock`() =
        runTest {
            val controller = unlockedController()

            controller.onAppBackgrounded()
            clock.advanceBy(AppLockController.LOCK_AFTER.minusSeconds(1))
            controller.onAppForegrounded()

            assertEquals(AppLockState.Unlocked, controller.state.value)
        }

    @Test
    fun `coming back after the lock delay locks`() =
        runTest {
            val controller = unlockedController()

            controller.onAppBackgrounded()
            clock.advanceBy(AppLockController.LOCK_AFTER)
            controller.onAppForegrounded()

            assertEquals(AppLockState.Locked, controller.state.value)
        }

    /**
     * The PIN screen is another app. When the unlock result arrives before the app is back in the
     * foreground, a minute spent typing the PIN must not count as a minute away.
     */
    @Test
    fun `a slowly typed PIN does not lock the app again once it is back`() =
        runTest {
            val controller = controller(appLockEnabled = true)

            controller.onAppBackgrounded()
            clock.advanceBy(AppLockController.LOCK_AFTER.multipliedBy(2))
            controller.onAuthenticated()
            controller.onAppForegrounded()

            assertEquals(AppLockState.Unlocked, controller.state.value)
        }

    // -- unlocking ------------------------------------------------------------------------------------

    @Test
    fun `a cancelled prompt stays locked`() =
        runTest {
            val controller = controller(appLockEnabled = true)

            controller.onAuthenticationEnded()

            assertEquals(AppLockState.Locked, controller.state.value)
        }

    @Test
    fun `a prompt that fails because the screen lock is gone opens the app`() =
        runTest {
            val controller = controller(appLockEnabled = true)
            authenticator.canAuthenticate = false

            controller.onAuthenticationEnded()

            assertEquals(AppLockState.Unlocked, controller.state.value)
        }

    // -- the setting ----------------------------------------------------------------------------------

    /** Whoever just turned it on is holding the phone. */
    @Test
    fun `turning app lock on does not lock straight away`() =
        runTest {
            val preferences = TestUserPreferencesRepository(appLockEnabled = false)
            val controller = controller(preferences)

            preferences.setAppLockEnabled(true)

            assertEquals(AppLockState.Unlocked, controller.state.value)
        }

    @Test
    fun `turning app lock off unlocks`() =
        runTest {
            val preferences = TestUserPreferencesRepository(appLockEnabled = true)
            val controller = controller(preferences)

            preferences.setAppLockEnabled(false)

            assertEquals(AppLockState.Unlocked, controller.state.value)
        }

    // -- helpers --------------------------------------------------------------------------------------

    private fun TestScope.unlockedController(): AppLockController =
        controller(appLockEnabled = true).also { it.onAuthenticated() }

    private fun TestScope.controller(appLockEnabled: Boolean) =
        controller(TestUserPreferencesRepository(appLockEnabled = appLockEnabled))

    private fun TestScope.controller(preferences: TestUserPreferencesRepository) =
        AppLockController(
            userPreferences = preferences,
            authenticator = authenticator,
            clock = clock,
            // Unconfined, so the setting is read before the constructor returns, as it effectively is by
            // the time a real screen draws; the test's background scope, so the collection ends with it.
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )

    private class FakeDeviceAuthenticator : DeviceAuthenticator {
        var canAuthenticate = true

        override fun canAuthenticate() = canAuthenticate
    }

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        fun advanceBy(duration: java.time.Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }
}
