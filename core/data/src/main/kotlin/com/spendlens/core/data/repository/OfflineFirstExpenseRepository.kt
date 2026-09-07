package com.spendlens.core.data.repository

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.asDomainModel
import com.spendlens.core.database.entity.asEntity
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The local database is the source of truth; the network is a later input to it, never a read path.
 *
 * [clock] is injected rather than calling `Instant.now()`. A repository that reads the wall clock
 * cannot be asserted against — the test would have to compare against "roughly now" — whereas a
 * `Clock.fixed` makes `updatedAt` an exact expected value.
 */
class OfflineFirstExpenseRepository
    @Inject
    constructor(
        private val expenseDao: ExpenseDao,
        private val clock: Clock,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : ExpenseRepository {
        override fun observeExpenses(): Flow<List<Expense>> =
            expenseDao.observeExpenses().map { entities -> entities.map { it.asDomainModel() } }

        override fun observeExpense(id: String): Flow<Expense?> =
            expenseDao.observeExpense(id).map { it?.asDomainModel() }

        /**
         * The month-to-instant conversion lives here rather than in the DAO: SQL should compare
         * numbers, and deciding *which* numbers is timezone logic that belongs in one place.
         */
        override fun observeExpensesIn(
            month: YearMonth,
            zoneId: ZoneId,
        ): Flow<List<Expense>> {
            val start = month.atDay(1).atStartOfDay(zoneId).toInstant()
            val end = month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(zoneId)
                .toInstant()
            return expenseDao
                .observeExpensesBetween(start.toEpochMilli(), end.toEpochMilli())
                .map { entities -> entities.map { it.asDomainModel() } }
        }

        override suspend fun upsert(expense: Expense) =
            withContext(ioDispatcher) {
                expenseDao.upsert(
                    expense
                        .copy(
                            // Any local write is unsynced by definition, whatever the caller passed.
                            syncState = SyncState.PENDING,
                            updatedAt = clock.instant(),
                        ).asEntity(),
                )
            }

        override suspend fun delete(id: String) =
            withContext(ioDispatcher) {
                expenseDao.softDelete(id = id, updatedAt = clock.millis())
            }
    }
