package com.spendlens.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.spendlens.core.data.sync.SyncReport
import com.spendlens.sync.WorkManagerSyncManager.Companion.PERIODIC_SYNC_WORK_NAME
import com.spendlens.sync.WorkManagerSyncManager.Companion.SYNC_WORK_NAME
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How requests turn into queued work, against WorkManager's own test implementation — real queueing
 * rules and real constraints, with the work run on demand.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager
    private lateinit var syncManager: WorkManagerSyncManager

    /** What each sync the worker runs does. Replaced by tests that need to hold one open. */
    private var sync: suspend () -> SyncReport = { SyncReport() }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factoryFor(FakeSynchronizer { sync() }))
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        syncManager = WorkManagerSyncManager(context)
    }

    @Test
    fun `a request queues a sync that waits for a network connection`() =
        runTest {
            syncManager.requestSync()

            val sync = syncWork().single()
            assertEquals(WorkInfo.State.ENQUEUED, sync.state)
            assertEquals(NetworkType.CONNECTED, sync.constraints.requiredNetworkType)
        }

    /** Fifty offline saves must not become fifty syncs when the connection comes back. */
    @Test
    fun `requests made while a sync is waiting do not pile up`() =
        runTest {
            repeat(50) { syncManager.requestSync() }

            assertEquals(1, syncWork().size)
        }

    @Test
    fun `a request after a sync has finished queues a new one`() =
        runTest {
            syncManager.requestSync()
            runQueuedSync()

            syncManager.requestSync()

            assertEquals(
                listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED),
                syncWork().map { it.state }.sortedBy { it.ordinal }.reversed(),
            )
        }

    /**
     * The case the whole policy exists for. The running sync may already have read its pending rows, so
     * a save now needs a sync queued behind it — and only one, however many saves follow.
     */
    @Test
    fun `a request while a sync is running queues exactly one to run after it`() =
        runTest {
            val finishRunningSync = CompletableDeferred<SyncReport>()
            sync = { finishRunningSync.await() }
            syncManager.requestSync()
            val running = syncWork().single().id
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(running)
            awaitState(running, WorkInfo.State.RUNNING)

            repeat(3) { syncManager.requestSync() }

            assertEquals(
                listOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED),
                syncWork().map { it.state }.sortedBy { it.ordinal },
            )
            finishRunningSync.complete(SyncReport())
            awaitState(running, WorkInfo.State.SUCCEEDED)
        }

    @Test
    fun `initialize schedules the periodic sync and one sync now`() =
        runTest {
            syncManager.initialize()
            syncManager.initialize()

            assertEquals(1, workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK_NAME).first().size)
            assertEquals(1, syncWork().size)
        }

    // -- which states count as "already waiting" ----------------------------------------------------

    @Test
    fun `a queued or blocked sync will see a new save`() {
        assertTrue(listOf(WorkInfo.State.ENQUEUED).hasSyncWaitingToStart())
        assertTrue(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.BLOCKED).hasSyncWaitingToStart())
    }

    /** A running sync may already have read its pending rows; a save during it needs a sync after it. */
    @Test
    fun `a running or finished sync will not`() {
        assertFalse(listOf(WorkInfo.State.RUNNING).hasSyncWaitingToStart())
        assertFalse(
            listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).hasSyncWaitingToStart(),
        )
        assertFalse(emptyList<WorkInfo.State>().hasSyncWaitingToStart())
    }

    private suspend fun syncWork(): List<WorkInfo> = workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME).first()

    /** Pretends the network arrived, then waits for the worker to finish. */
    private suspend fun runQueuedSync() {
        val id = syncWork().single().id
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(id)
        awaitState(id, WorkInfo.State.SUCCEEDED)
    }

    /** In real time: the worker runs on WorkManager's threads, which the test's virtual clock cannot advance. */
    private suspend fun awaitState(
        id: java.util.UUID,
        state: WorkInfo.State,
    ) = withContext(Dispatchers.Default) {
        withTimeout(WORKER_TIMEOUT_MS) {
            workManager.getWorkInfoByIdFlow(id).first { it?.state == state }
        }
    }

    private companion object {
        const val WORKER_TIMEOUT_MS = 5_000L
    }
}
