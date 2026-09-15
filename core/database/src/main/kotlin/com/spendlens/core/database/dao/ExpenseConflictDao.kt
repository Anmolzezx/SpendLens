package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.ExpenseConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseConflictDao {
    @Query("SELECT * FROM expense_conflicts WHERE expense_id = :expenseId")
    suspend fun getConflict(expenseId: String): ExpenseConflictEntity?

    @Query("SELECT * FROM expense_conflicts WHERE expense_id = :expenseId")
    fun observeConflict(expenseId: String): Flow<ExpenseConflictEntity?>

    /** In a stable order, so "review" offers the same conflict first every time until it is settled. */
    @Query("SELECT expense_id FROM expense_conflicts ORDER BY server_version")
    fun observeConflictedExpenseIds(): Flow<List<String>>

    /** Upsert: if the other device edits again before the user decides, its newest copy replaces this. */
    @Upsert
    suspend fun upsert(conflict: ExpenseConflictEntity)

    @Query("DELETE FROM expense_conflicts WHERE expense_id = :expenseId")
    suspend fun delete(expenseId: String)
}
