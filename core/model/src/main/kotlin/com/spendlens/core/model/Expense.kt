package com.spendlens.core.model

import java.time.Instant

/**
 * A single recorded expense. The domain type — no Android, Room, or network concerns.
 *
 * Three decisions worth being able to defend:
 *
 *  - [id] is a client-generated UUID, not a server-assigned key. An offline-first app has to be able
 *    to create a record with no network, and a record needs a stable identity the moment it exists.
 *  - [amountMinor] is a whole number of minor units (cents, paise), never a `Double`. Binary floating
 *    point cannot represent 0.10 exactly, and money arithmetic has to be exact.
 *  - [isDeleted] is a tombstone rather than a real delete. A hard delete cannot be synced — the other
 *    device has no way to tell "deleted" apart from "never seen".
 */
data class Expense(
    val id: String,
    val merchant: String,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Instant,
    val categoryId: String,
    val note: String?,
    val receiptImagePath: String?,
    val syncState: SyncState,
    /** Last local modification. Drives conflict resolution on sync. */
    val updatedAt: Instant,
    val isDeleted: Boolean,
)
