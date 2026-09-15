package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    /**
     * Every read filters `is_deleted = 0`. Tombstones are rows the sync layer needs and the UI must
     * never see, and leaving that filter to the caller is how one screen eventually forgets it.
     *
     * `updated_at` breaks ties. Dates are calendar days, so several expenses routinely share one;
     * without a second key their order would be whatever SQLite happens to return.
     */
    @Query("SELECT * FROM expenses WHERE is_deleted = 0 ORDER BY occurred_at DESC, updated_at DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id AND is_deleted = 0")
    fun observeExpense(id: String): Flow<ExpenseEntity?>

    @Query(
        """
        SELECT * FROM expenses
        WHERE is_deleted = 0 AND occurred_at >= :startDayInclusive AND occurred_at < :endDayExclusive
        ORDER BY occurred_at DESC, updated_at DESC
        """,
    )
    fun observeExpensesBetween(
        startDayInclusive: Long,
        endDayExclusive: Long,
    ): Flow<List<ExpenseEntity>>

    /** Tombstones included — the sync layer has to upload deletions too. */
    @Query("SELECT * FROM expenses WHERE sync_state != :synced")
    suspend fun getPendingSync(synced: SyncState = SyncState.SYNCED): List<ExpenseEntity>

    /**
     * `@Upsert`, not `@Insert` + `@Update`. Writes arrive from two directions — the user editing a
     * row, and sync pulling one that may or may not exist locally — and "insert or replace" is the
     * same operation in both cases.
     */
    @Upsert
    suspend fun upsert(expenses: List<ExpenseEntity>)

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    /**
     * Soft delete. A hard delete cannot be synced: the other device has no way to tell "deleted"
     * from "never seen", and the row resurrects on the next pull.
     */
    @Query("UPDATE expenses SET is_deleted = 1, sync_state = :pending, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(
        id: String,
        updatedAt: Long,
        pending: SyncState = SyncState.PENDING,
    )

    /** Hard delete, for purging synced tombstones. Not reachable from the UI. */
    @Query("DELETE FROM expenses WHERE is_deleted = 1 AND sync_state = :synced AND updated_at < :olderThan")
    suspend fun purgeTombstones(
        olderThan: Long,
        synced: SyncState = SyncState.SYNCED,
    ): Int
}
