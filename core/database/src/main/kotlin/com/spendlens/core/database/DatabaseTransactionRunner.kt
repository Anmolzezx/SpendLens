package com.spendlens.core.database

import androidx.room.withTransaction

/**
 * Runs a block of DAO calls as one transaction, without the caller depending on Room.
 *
 * Needed wherever code reads a row and then writes on the strength of what it read. Sync applying a
 * server change and the user saving an edit can otherwise interleave, and one silently overwrites the
 * other.
 */
interface DatabaseTransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

class RoomTransactionRunner(
    private val database: SpendLensDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction(block)
}
