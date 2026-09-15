package com.spendlens.core.testing.sync

import com.spendlens.core.data.sync.SyncStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class TestSyncStatusRepository(
    lastSyncedAt: Instant? = null,
) : SyncStatusRepository {
    /** Set to simulate a sync finishing. */
    val lastSyncedAt = MutableStateFlow(lastSyncedAt)

    override fun observeLastSyncedAt() = lastSyncedAt
}
