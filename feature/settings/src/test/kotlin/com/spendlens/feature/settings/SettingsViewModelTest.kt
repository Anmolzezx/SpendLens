package com.spendlens.feature.settings

import app.cash.turbine.test
import com.spendlens.core.applock.DeviceAuthenticator
import com.spendlens.core.testing.repository.TestUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val authenticator = FakeDeviceAuthenticator()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `turning app lock on saves it`() =
        runTest {
            val preferences = TestUserPreferencesRepository()
            val viewModel = SettingsViewModel(preferences, authenticator)

            viewModel.setAppLockEnabled(true)

            assertTrue(preferences.appLockEnabled.value)
        }

    @Test
    fun `without a screen lock, app lock cannot be turned on`() =
        runTest {
            authenticator.canAuthenticate = false
            val viewModel = SettingsViewModel(TestUserPreferencesRepository(appLockEnabled = false), authenticator)

            viewModel.uiState.test {
                assertFalse((awaitItem() as SettingsUiState.Success).canChangeAppLock)
            }
        }

    /** Screen lock removed after turning app lock on: the saved "on" must stay visible and switchable off. */
    @Test
    fun `without a screen lock, app lock that is on can still be turned off`() =
        runTest {
            authenticator.canAuthenticate = false
            val viewModel = SettingsViewModel(TestUserPreferencesRepository(appLockEnabled = true), authenticator)

            viewModel.uiState.test {
                val state = awaitItem() as SettingsUiState.Success
                assertTrue(state.appLockEnabled)
                assertTrue(state.canChangeAppLock)
            }
        }

    /** Off to system settings to add a PIN, then back: the switch becomes usable without a restart. */
    @Test
    fun `setting up a screen lock is noticed on return`() =
        runTest {
            authenticator.canAuthenticate = false
            val viewModel = SettingsViewModel(TestUserPreferencesRepository(), authenticator)

            viewModel.uiState.test {
                assertEquals(SettingsUiState.Success(appLockEnabled = false, canUseAppLock = false), awaitItem())

                authenticator.canAuthenticate = true
                viewModel.refreshDeviceSecurity()

                assertEquals(SettingsUiState.Success(appLockEnabled = false, canUseAppLock = true), awaitItem())
            }
        }

    private class FakeDeviceAuthenticator : DeviceAuthenticator {
        var canAuthenticate = true

        override fun canAuthenticate() = canAuthenticate
    }
}
