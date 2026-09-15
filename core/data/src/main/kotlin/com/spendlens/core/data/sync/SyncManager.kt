package com.spendlens.core.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * Asks for sync to happen, without knowing how it happens.
 *
 * Repositories call [requestSync] after a local write. The implementation lives in `:sync`, on
 * WorkManager; `core:data` and every feature stay free of it, and their tests use a fake.
 */
interface SyncManager {
    /** True while a sync is running. */
    val isSyncing: Flow<Boolean>

    /**
     * Queues a sync for the next time the network allows. Returns once it is queued, not once it has
     * run — the write that called this must never wait on the network.
     */
    suspend fun requestSync()

    /**
     * The user asked for a sync. Unlike [requestSync], this does not wait out a retry backoff: a sync
     * sitting out a failure starts again straight away. A sync already running is left alone —
     * interrupting one mid-upload could record the upload as never having happened.
     */
    suspend fun syncNow()
}
