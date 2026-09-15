package com.spendlens.core.data.repository

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.data.sync.SyncManager
import com.spendlens.core.database.DatabaseTransactionRunner
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
        private val transaction: DatabaseTransactionRunner,
        private val syncManager: SyncManager,
        private val clock: Clock,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : ExpenseRepository {
        override fun observeExpenses(): Flow<List<Expense>> =
            expenseDao.observeExpenses().map { entities -> entities.map { it.asDomainModel() } }

        override fun observeExpense(id: String): Flow<Expense?> =
            expenseDao.observeExpense(id).map { it?.asDomainModel() }

        /** The DAO compares epoch days; turning a month into that range is done once, here. */
        override fun observeExpensesIn(month: YearMonth): Flow<List<Expense>> =
            expenseDao
                .observeExpensesBetween(
                    startDayInclusive = month.atDay(1).toEpochDay(),
                    endDayExclusive = month.plusMonths(1).atDay(1).toEpochDay(),
                ).map { entities -> entities.map { it.asDomainModel() } }

        /**
         * Sync bookkeeping comes from the stored row, never from the caller. The edit screen builds a
         * fresh `Expense` from its form, so trusting its `remoteVersion` (null) would make every edit of
         * a synced expense look to the server like a brand-new record colliding with an existing one.
         *
         * Read and write share a transaction, so a sync recording a newer version in between cannot be
         * overwritten with the old one.
         */
        override suspend fun upsert(expense: Expense) =
            withContext(ioDispatcher) {
                transaction {
                    val stored = expenseDao.getExpense(expense.id)
                    expenseDao.upsert(
                        expense
                            .copy(
                                // A local write is unsynced by definition. A conflicted row stays
                                // conflicted: the edit changes this device's side, it does not settle it.
                                syncState = if (stored?.syncState == SyncState.CONFLICT) {
                                    SyncState.CONFLICT
                                } else {
                                    SyncState.PENDING
                                },
                                updatedAt = clock.instant(),
                                remoteVersion = stored?.remoteVersion,
                            ).asEntity(),
                    )
                }
                // After the transaction commits, so the sync it starts is guaranteed to see this write.
                syncManager.requestSync()
            }

        override suspend fun delete(id: String) =
            withContext(ioDispatcher) {
                expenseDao.softDelete(id = id, updatedAt = clock.millis())
                syncManager.requestSync()
            }
    }
