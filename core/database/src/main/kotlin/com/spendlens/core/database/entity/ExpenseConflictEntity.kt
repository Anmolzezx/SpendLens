package com.spendlens.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendlens.core.model.SyncState
import java.time.Instant
import java.time.LocalDate

/**
 * The server's side of a conflict: its copy of an expense this device also changed.
 *
 * This device's side stays where it was, in `expenses`, marked `CONFLICT` — so the list keeps showing
 * what the user last typed. This row holds the other version until they choose.
 *
 * A separate table rather than a second set of columns on `expenses`: conflicts are rare, and doubling
 * the width of every row for them would put sync's bookkeeping into the table every screen reads.
 */
@Entity(tableName = "expense_conflicts")
data class ExpenseConflictEntity(
    @PrimaryKey
    @ColumnInfo(name = "expense_id")
    val expenseId: String,
    val merchant: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "occurred_at")
    val occurredOn: LocalDate,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    val note: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean,
    /** Becomes the local row's `remote_version` whichever side the user keeps. */
    @ColumnInfo(name = "server_version")
    val serverVersion: Long,
)

/**
 * The server's version as a synced local row. [receiptImagePath] comes from the local row: the server
 * never has one, and taking its content must not throw away a photo that is still on this device.
 */
fun ExpenseConflictEntity.asSyncedExpense(receiptImagePath: String?) =
    ExpenseEntity(
        id = expenseId,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredOn = occurredOn,
        categoryId = categoryId,
        note = note,
        receiptImagePath = receiptImagePath,
        syncState = SyncState.SYNCED,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        remoteVersion = serverVersion,
    )
