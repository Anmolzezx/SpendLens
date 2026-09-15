package com.spendlens.core.data.sync

import com.spendlens.core.database.dao.SyncCursorDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

/** The name expenses' sync position is stored under. One stream today; budgets would be another. */
internal const val EXPENSES_SYNC_STREAM = "expenses"

interface SyncStatusRepository {
    /** When this device was last fully up to date with the server, or null if it never has been. */
    fun observeLastSyncedAt(): Flow<Instant?>
}

class OfflineFirstSyncStatusRepository
    @Inject
    constructor(
        private val cursorDao: SyncCursorDao,
    ) : SyncStatusRepository {
        override fun observeLastSyncedAt(): Flow<Instant?> = cursorDao.observeLastSyncedAt(EXPENSES_SYNC_STREAM)
    }
