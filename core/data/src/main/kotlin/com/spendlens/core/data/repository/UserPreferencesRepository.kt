package com.spendlens.core.data.repository

import com.spendlens.core.datastore.UserPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface UserPreferencesRepository {
    val appLockEnabled: Flow<Boolean>

    suspend fun setAppLockEnabled(enabled: Boolean)
}

class DataStoreUserPreferencesRepository
    @Inject
    constructor(
        private val dataSource: UserPreferencesDataSource,
    ) : UserPreferencesRepository {
        override val appLockEnabled: Flow<Boolean> = dataSource.appLockEnabled

        override suspend fun setAppLockEnabled(enabled: Boolean) = dataSource.setAppLockEnabled(enabled)
    }
