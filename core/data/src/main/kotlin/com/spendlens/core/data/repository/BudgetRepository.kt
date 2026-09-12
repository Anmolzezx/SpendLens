package com.spendlens.core.data.repository

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.database.dao.BudgetDao
import com.spendlens.core.database.entity.BudgetEntity
import com.spendlens.core.database.entity.asDomainModel
import com.spendlens.core.model.Budget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.YearMonth
import javax.inject.Inject

interface BudgetRepository {
    fun observeBudgets(month: YearMonth): Flow<List<Budget>>

    suspend fun setBudget(
        categoryId: String,
        limitMinor: Long,
        currency: String,
        month: YearMonth,
    )

    suspend fun clearBudget(
        categoryId: String,
        month: YearMonth,
    )
}

class OfflineFirstBudgetRepository
    @Inject
    constructor(
        private val budgetDao: BudgetDao,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : BudgetRepository {
        override fun observeBudgets(month: YearMonth): Flow<List<Budget>> =
            budgetDao
                .observeBudgets(month.toString())
                .map { entities -> entities.map { it.asDomainModel() } }

        override suspend fun setBudget(
            categoryId: String,
            limitMinor: Long,
            currency: String,
            month: YearMonth,
        ) = withContext(ioDispatcher) {
            budgetDao.upsert(
                BudgetEntity(
                    categoryId = categoryId,
                    month = month,
                    limitMinor = limitMinor,
                    currency = currency,
                ),
            )
        }

        override suspend fun clearBudget(
            categoryId: String,
            month: YearMonth,
        ) = withContext(ioDispatcher) {
            budgetDao.clear(categoryId, month.toString())
        }
    }
