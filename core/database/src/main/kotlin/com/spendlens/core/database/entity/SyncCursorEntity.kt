package com.spendlens.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * How far this device has pulled, per stream of records.
 *
 * In Room rather than DataStore so it commits in the **same transaction** as the page of changes it
 * describes. Stored separately, a crash between the two could leave the cursor ahead of the data —
 * and those changes would never be downloaded.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey
    val stream: String,
    val cursor: Long,
    /**
     * When a pass last completed: everything uploaded, then every page downloaded. Written in the same
     * transaction as the final page, so it can never claim a sync that did not finish. Null until the
     * first one does.
     */
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Instant? = null,
)
