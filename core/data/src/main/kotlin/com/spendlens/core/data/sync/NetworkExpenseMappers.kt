package com.spendlens.core.data.sync

import com.spendlens.core.database.entity.ExpenseConflictEntity
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.model.SyncState
import com.spendlens.core.protocol.NetworkExpense
import java.time.Instant
import java.time.LocalDate

// Lives in core:data, not core:network: turning a server record into a row needs local-only facts —
// the device's receipt photo, its sync state — that the network module has no business knowing.

internal fun ExpenseEntity.asNetworkExpense() =
    NetworkExpense(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredOn = occurredOn.toString(),
        categoryId = categoryId,
        note = note,
        isDeleted = isDeleted,
        updatedAt = updatedAt.toString(),
    )

/** [receiptImagePath] is the local row's, if there is one: the server never sends a photo path. */
internal fun NetworkExpense.asSyncedEntity(receiptImagePath: String?) =
    ExpenseEntity(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredOn = LocalDate.parse(occurredOn),
        categoryId = categoryId,
        note = note,
        receiptImagePath = receiptImagePath,
        syncState = SyncState.SYNCED,
        updatedAt = Instant.parse(updatedAt),
        isDeleted = isDeleted,
        remoteVersion = serverVersion(),
    )

internal fun NetworkExpense.asConflictEntity() =
    ExpenseConflictEntity(
        expenseId = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredOn = LocalDate.parse(occurredOn),
        categoryId = categoryId,
        note = note,
        updatedAt = Instant.parse(updatedAt),
        isDeleted = isDeleted,
        serverVersion = serverVersion(),
    )

/**
 * Whether two versions say the same thing, so that both devices having changed a record is not
 * automatically a conflict.
 *
 * Compares what the user typed. Not `updatedAt` — two devices never make the same edit at the same
 * millisecond — and not the receipt path, which is local to each device. Two deletions agree whatever
 * the deleted rows last contained.
 */
internal fun ExpenseEntity.hasSameContentAs(remote: NetworkExpense): Boolean =
    if (isDeleted && remote.isDeleted) {
        true
    } else {
        isDeleted == remote.isDeleted &&
            merchant == remote.merchant &&
            amountMinor == remote.amountMinor &&
            currency == remote.currency &&
            occurredOn.toString() == remote.occurredOn &&
            categoryId == remote.categoryId &&
            note == remote.note
    }

internal fun NetworkExpense.serverVersion(): Long =
    checkNotNull(version) { "Server sent expense $id without a version" }
