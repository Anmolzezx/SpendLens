package com.spendlens.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.spendlens.core.data.sync.SyncReport
import com.spendlens.core.data.sync.Synchronizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** What the worker tells WorkManager, for each way a sync can end. */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `succeeds when the sync completes`() =
        runTest {
            assertEquals(Result.success(), worker { SyncReport(pushed = 1) }.doWork())
        }

    @Test
    fun `retries after a network failure`() =
        runTest {
            assertEquals(Result.retry(), worker { throw IOException("offline") }.doWork())
        }

    @Test
    fun `retries on every attempt before the last`() =
        runTest {
            val lastButOne = SyncWorker.MAX_ATTEMPTS - 2

            assertEquals(Result.retry(), worker(runAttemptCount = lastButOne) { throw IOException() }.doWork())
        }

    @Test
    fun `stops retrying on the last attempt`() =
        runTest {
            val last = SyncWorker.MAX_ATTEMPTS - 1

            assertEquals(Result.failure(), worker(runAttemptCount = last) { throw IOException() }.doWork())
        }

    /** Retrying cannot fix a server that breaks the protocol; it would only fail the same way, again. */
    @Test
    fun `does not retry a failure that is not the network's`() =
        runTest {
            val failure = runCatching { worker { error("Server sent expense a without a version") }.doWork() }

            assertTrue(failure.exceptionOrNull() is IllegalStateException)
        }

    private fun worker(
        runAttemptCount: Int = 0,
        sync: suspend () -> SyncReport,
    ): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(factoryFor(FakeSynchronizer(sync)))
            .build()
}

internal class FakeSynchronizer(
    private val sync: suspend () -> SyncReport = { SyncReport() },
) : Synchronizer {
    override suspend fun sync(): SyncReport = sync.invoke()
}

/** Builds [SyncWorker] by hand, standing in for the factory Hilt generates in the app. */
internal fun factoryFor(synchronizer: Synchronizer) =
    object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = SyncWorker(appContext, workerParameters, synchronizer)
    }
