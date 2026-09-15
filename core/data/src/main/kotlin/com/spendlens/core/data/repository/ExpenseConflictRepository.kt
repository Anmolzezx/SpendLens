package com.spendlens.core.data.repository

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.database.DatabaseTransactionRunner
import com.spendlens.core.database.dao.ExpenseConflictDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.asSyncedExpense
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

/** The user's decision on an expense in `CONFLICT`. */
interface ExpenseConflictRepository {
    /**
     * Keep this device's version. It is re-based onto the server's, so the next sync uploads it as an
     * ordinary edit — one made after seeing the other side — and the server accepts it.
     */
    suspend fun keepLocal(expenseId: String)

    /** Take the other device's version and discard this one's. */
    suspend fun keepRemote(expenseId: String)
}

class OfflineFirstExpenseConflictRepository
    @Inject
    constructor(
        private val expenseDao: ExpenseDao,
        private val conflictDao: ExpenseConflictDao,
        private val transaction: DatabaseTransactionRunner,
        private val clock: Clock,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : ExpenseConflictRepository {
        override suspend fun keepLocal(expenseId: String) =
            withContext(ioDispatcher) {
                transaction {
                    val conflict = conflictDao.getConflict(expenseId) ?: return@transaction
                    val local = expenseDao.getExpense(expenseId) ?: return@transaction
                    expenseDao.upsert(
                        local.copy(
                            syncState = SyncState.PENDING,
                            updatedAt = clock.instant(),
                            remoteVersion = conflict.serverVersion,
                        ),
                    )
                    conflictDao.delete(expenseId)
                }
            }

        override suspend fun keepRemote(expenseId: String) =
            withContext(ioDispatcher) {
                transaction {
                    val conflict = conflictDao.getConflict(expenseId) ?: return@transaction
                    val local = expenseDao.getExpense(expenseId)
                    expenseDao.upsert(conflict.asSyncedExpense(receiptImagePath = local?.receiptImagePath))
                    conflictDao.delete(expenseId)
                }
            }
    }
