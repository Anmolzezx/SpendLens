package com.spendlens.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
