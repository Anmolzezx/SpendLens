package com.spendlens.sync

import javax.inject.Inject

/**
 * Starts background sync: the periodic safety net, plus one pass now to catch up after the app was
 * closed. Call once, from `Application.onCreate`.
 *
 * The only public entry point to this module besides the worker itself — `:app` needs to start sync,
 * not to know it runs on WorkManager.
 */
class SyncInitializer
    @Inject
    internal constructor(
        private val syncManager: WorkManagerSyncManager,
    ) {
        fun initialize() = syncManager.initialize()
    }
