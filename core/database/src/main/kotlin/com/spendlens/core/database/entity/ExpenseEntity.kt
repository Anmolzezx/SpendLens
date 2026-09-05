package com.spendlens.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import java.time.Instant

/**
 * The storage shape of an [Expense].
 *
 * A separate type from the domain model on purpose. They look almost identical today, and that is
 * exactly when people collapse them — then the sync layer needs a column the UI has no business
 * knowing about, and every screen has to change. The mapping below is the seam that prevents it.
 *
 * Indexed on `occurred_at` because the list screen's only query orders by it, and on `sync_state`
 * because the sync worker's only query filters by it.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["sync_state"]),
        Index(value = ["category_id"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val merchant: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Instant,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    val note: String?,
    @ColumnInfo(name = "receipt_image_path")
    val receiptImagePath: String?,
    @ColumnInfo(name = "sync_state")
    val syncState: SyncState,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean,
)

fun ExpenseEntity.asDomainModel() =
    Expense(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        categoryId = categoryId,
        note = note,
        receiptImagePath = receiptImagePath,
        syncState = syncState,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )

fun Expense.asEntity() =
    ExpenseEntity(
        id = id,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        categoryId = categoryId,
        note = note,
        receiptImagePath = receiptImagePath,
        syncState = syncState,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )
