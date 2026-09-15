package com.spendlens.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * The user's settings.
 *
 * DataStore, not Room: these are a handful of independent values with no relationships and no need to
 * commit alongside expense data — the reason the sync cursor went into Room does not apply. And not
 * `SharedPreferences`, whose synchronous API reads from disk on whichever thread asks, main included.
 *
 * Preferences DataStore rather than Proto: one boolean does not justify a protobuf schema and its build
 * plugin. That changes if settings grow structured.
 */
class UserPreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Off unless turned on. A lock the user did not ask for is a lock they cannot explain — and on a
         * device whose screen lock they later remove, it would be one they cannot open.
         */
        val appLockEnabled: Flow<Boolean> =
            dataStore.data
                // A corrupt or unreadable file means defaults, not a crash on every launch.
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { it[APP_LOCK_ENABLED] ?: false }

        suspend fun setAppLockEnabled(enabled: Boolean) {
            dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
        }

        private companion object {
            val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        }
    }
