package com.spendlens.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A single recorded expense. The domain type — no Android, Room, or network concerns.
 *
 * Decisions worth being able to defend:
 *
 *  - [id] is a client-generated UUID, not a server-assigned key. An offline-first app has to be able
 *    to create a record with no network, and a record needs a stable identity the moment it exists.
 *  - [amountMinor] is a whole number of minor units (cents, paise), never a `Double`. Binary floating
 *    point cannot represent 0.10 exactly, and money arithmetic has to be exact.
 *  - [occurredOn] is a calendar date, not an instant. See its own comment.
 *  - [isDeleted] is a tombstone rather than a real delete. A hard delete cannot be synced — the other
 *    device has no way to tell "deleted" apart from "never seen".
 */
data class Expense(
    val id: String,
    val merchant: String,
    val amountMinor: Long,
    val currency: String,
    /**
     * The day the money was spent, as the user states it.
     *
     * A `LocalDate` rather than an `Instant`, decided on 2026-09-15. An instant has no calendar date
     * until a timezone is applied, so an expense recorded at 11pm in Mumbai used to display as the
     * previous day in New York — and could land in a different month's budget. A receipt dated the
     * 26th is dated the 26th everywhere.
     */
    val occurredOn: LocalDate,
    val categoryId: String,
    val note: String?,
    val receiptImagePath: String?,
    val syncState: SyncState,
    /** Last local modification. An instant, because it genuinely is one: sync orders edits by it. */
    val updatedAt: Instant,
    val isDeleted: Boolean,
    /**
     * The server's version of this record as of the last successful sync; null if never synced.
     *
     * This is what makes [SyncState.CONFLICT] detectable. A local edit is marked `PENDING`; if, at
     * upload, the server's version no longer matches this one, someone else changed it too — both
     * sides diverged from a common ancestor, and neither edit may be silently dropped.
     */
    val remoteVersion: Long? = null,
)
