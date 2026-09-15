package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.SyncCursorEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SyncCursorDao {
    /** Null until the stream's first pull. */
    @Query("SELECT * FROM sync_cursors WHERE stream = :stream")
    suspend fun getSyncCursor(stream: String): SyncCursorEntity?

    @Query("SELECT last_synced_at FROM sync_cursors WHERE stream = :stream")
    fun observeLastSyncedAt(stream: String): Flow<Instant?>

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)
}
