package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.SyncCursorEntity

@Dao
interface SyncCursorDao {
    /** Null until the stream's first pull. */
    @Query("SELECT cursor FROM sync_cursors WHERE stream = :stream")
    suspend fun getCursor(stream: String): Long?

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)
}
