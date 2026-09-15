package com.spendlens.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Against a real DataStore on a real file — the persistence is the thing being tested. */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `app lock is off until turned on`() =
        runTest {
            withDataSource(folder.newFolder().resolve("prefs.preferences_pb")) {
                assertFalse(it.appLockEnabled.first())
            }
        }

    @Test
    fun `turning app lock on is remembered after the app restarts`() =
        runTest {
            val file = folder.newFolder().resolve("prefs.preferences_pb")

            withDataSource(file) { it.setAppLockEnabled(true) }

            // A new DataStore on the same file: what a cold start sees.
            withDataSource(file) { assertTrue(it.appLockEnabled.first()) }
        }

    @Test
    fun `app lock can be turned off again`() =
        runTest {
            withDataSource(folder.newFolder().resolve("prefs.preferences_pb")) {
                it.setAppLockEnabled(true)
                it.setAppLockEnabled(false)

                assertFalse(it.appLockEnabled.first())
            }
        }

    /** DataStore allows one active instance per file, so each is shut down before the next opens. */
    private suspend fun withDataSource(
        file: File,
        block: suspend (UserPreferencesDataSource) -> Unit,
    ) {
        val job = Job()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + job),
            produceFile = { file },
        )
        try {
            block(UserPreferencesDataSource(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }
}
