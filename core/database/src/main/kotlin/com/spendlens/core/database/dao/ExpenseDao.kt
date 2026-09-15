package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    /**
     * Rows waiting to upload, tombstones included — deletions have to reach the server too.
     *
     * `PENDING` only. A `CONFLICT` row is not uploaded: the server has already refused it once, and
     * sending it again would be refused again until the user decides which version to keep.
     */
    @Query("SELECT * FROM expenses WHERE sync_state = :pending")
    suspend fun getPendingSync(pending: SyncState = SyncState.PENDING): List<ExpenseEntity>

    /** Tombstones included, unlike every `observe` query: sync needs to know a row was deleted. */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: String): ExpenseEntity?

    /**
     * Tombstones included. For the conflict screen only: an expense deleted here and edited elsewhere
     * is still one side of the conflict, and the user has to see it to choose.
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeExpenseIncludingDeleted(id: String): Flow<ExpenseEntity?>

    /**
     * Records that the server accepted an upload as [version].
     *
     * The row is marked `SYNCED` only if it is unchanged since it was read for upload — compared by
     * [pushedUpdatedAt]. If the user edited it while the request was in flight, it stays `PENDING`,
     * but still takes the new version, so that edit uploads next time based on the version the server
     * now has instead of being refused as a conflict with this device's own earlier change.
     *
     * One statement, so there is no window between checking the row and updating it.
     */
    @Query(
        """
        UPDATE expenses
        SET remote_version = :version,
            sync_state = CASE WHEN updated_at = :pushedUpdatedAt THEN :synced ELSE sync_state END
        WHERE id = :id
        """,
    )
    suspend fun markPushed(
        id: String,
        version: Long,
        pushedUpdatedAt: Instant,
        synced: SyncState = SyncState.SYNCED,
    )

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
     *
     * A row already in `CONFLICT` stays there — deleting it changes this device's side of the
     * conflict, it does not settle it.
     */
    @Query(
        """
        UPDATE expenses
        SET is_deleted = 1,
            updated_at = :updatedAt,
            sync_state = CASE WHEN sync_state = :conflict THEN sync_state ELSE :pending END
        WHERE id = :id
        """,
    )
    suspend fun softDelete(
        id: String,
        updatedAt: Long,
        pending: SyncState = SyncState.PENDING,
        conflict: SyncState = SyncState.CONFLICT,
    )

    /** Hard delete, for purging synced tombstones. Not reachable from the UI. */
    @Query("DELETE FROM expenses WHERE is_deleted = 1 AND sync_state = :synced AND updated_at < :olderThan")
    suspend fun purgeTombstones(
        olderThan: Long,
        synced: SyncState = SyncState.SYNCED,
    ): Int
}
