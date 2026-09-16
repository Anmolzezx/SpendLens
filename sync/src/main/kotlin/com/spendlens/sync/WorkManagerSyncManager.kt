package com.spendlens.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import com.spendlens.core.data.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncManager] on WorkManager, so a requested sync survives the app closing and waits for a network.
 *
 * **What a request does.** Every save calls [requestSync], and each must be followed by a sync that
 * *starts after* the save — otherwise that change waits hours for the periodic sync.
 *
 * - A sync already **queued but not started** will read the database when it starts, so it will see
 *   this save. Queueing another would be redundant: skip.
 * - A sync already **running** may have read its pending rows before this save. Append one to run
 *   after it.
 * - **Nothing** queued: start one.
 *
 * Neither built-in policy does that alone. `KEEP` drops the request while a sync runs, so the save is
 * missed. `APPEND` alone queues one sync per save, so fifty offline edits become fifty syncs when the
 * connection returns. Checking first keeps the queue at most one running and one waiting.
 */
@Singleton
internal class WorkManagerSyncManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SyncManager {
        // Lazy: this is injected into the Application, before WorkManager can be initialised from it.
        private val workManager by lazy { WorkManager.getInstance(context) }

        /** Serialises check-then-enqueue, so two saves at once cannot both see "nothing waiting". */
        private val mutex = Mutex()

        override val isSyncing: Flow<Boolean>
            get() = workManager
                .getWorkInfosFlow(WorkQuery.fromUniqueWorkNames(SYNC_WORK_NAME, PERIODIC_SYNC_WORK_NAME))
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
                .distinctUntilChanged()

        override suspend fun requestSync() =
            mutex.withLock {
                if (!syncStates().hasSyncWaitingToStart()) enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }

        /**
         * - **Running:** leave it. Cancelling a sync between the server accepting an upload and the
         *   device recording that would make the next pass look like a conflict with itself.
         * - **Waiting:** replace it. It may be sitting out minutes of retry backoff, and the user asked now.
         *   A waiting sync has read nothing yet, so replacing it loses nothing.
         * - **Nothing queued:** start one.
         */
        override suspend fun syncNow() =
            mutex.withLock {
                val states = syncStates()
                when {
                    WorkInfo.State.RUNNING in states -> Unit
                    states.hasSyncWaitingToStart() -> enqueue(ExistingWorkPolicy.REPLACE)
                    else -> enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
                }
            }

        private suspend fun syncStates(): List<WorkInfo.State> =
            workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME).first().map { it.state }

        /** Awaited, so the next request's check sees this one. */
        private suspend fun enqueue(policy: ExistingWorkPolicy) {
            workManager.enqueueUniqueWork(SYNC_WORK_NAME, policy, SyncWorker.oneTimeRequest()).await()
        }

        /**
         * Called once at launch. `KEEP` is right here, unlike in [requestSync]: nothing has been saved
         * in this process yet, so a sync already queued or running covers everything there is.
         */
        fun initialize() {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                SyncWorker.periodicRequest(),
            )
            workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, SyncWorker.oneTimeRequest())
        }

        internal companion object {
            const val SYNC_WORK_NAME = "sync"
            const val PERIODIC_SYNC_WORK_NAME = "sync-periodic"
        }
    }

/**
 * Whether a sync exists that has not read the database yet: queued, waiting on a constraint or
 * backoff (`ENQUEUED`), or waiting on the sync ahead of it (`BLOCKED`).
 */
internal fun List<WorkInfo.State>.hasSyncWaitingToStart(): Boolean =
    any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }
