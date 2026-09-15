package com.spendlens.core.testing.repository

import com.spendlens.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow

class TestUserPreferencesRepository(
    appLockEnabled: Boolean = false,
) : UserPreferencesRepository {
    override val appLockEnabled = MutableStateFlow(appLockEnabled)

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        appLockEnabled.value = enabled
    }
}
