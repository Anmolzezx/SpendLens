package com.spendlens.core.testing.sync

import com.spendlens.core.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Records sync requests instead of scheduling anything. */
class TestSyncManager : SyncManager {
    override val isSyncing = MutableStateFlow(false)

    var requestCount = 0
        private set

    override suspend fun requestSync() {
        requestCount++
    }
}
