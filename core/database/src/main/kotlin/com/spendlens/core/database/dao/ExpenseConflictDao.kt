package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.ExpenseConflictEntity

@Dao
interface ExpenseConflictDao {
    @Query("SELECT * FROM expense_conflicts WHERE expense_id = :expenseId")
    suspend fun getConflict(expenseId: String): ExpenseConflictEntity?

    /** Upsert: if the other device edits again before the user decides, its newest copy replaces this. */
    @Upsert
    suspend fun upsert(conflict: ExpenseConflictEntity)

    @Query("DELETE FROM expense_conflicts WHERE expense_id = :expenseId")
    suspend fun delete(expenseId: String)
}
