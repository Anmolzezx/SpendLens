package com.spendlens.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.spendlens.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month")
    fun observeBudgets(month: String): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /** Clearing a budget deletes the row — unlike expenses, a limit has nothing to sync. */
    @Query("DELETE FROM budgets WHERE category_id = :categoryId AND month = :month")
    suspend fun clear(
        categoryId: String,
        month: String,
    )
}
