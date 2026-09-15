package com.spendlens.core.data.repository

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.data.sync.SyncManager
import com.spendlens.core.database.DatabaseTransactionRunner
import com.spendlens.core.database.dao.ExpenseConflictDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.asDomainModel
import com.spendlens.core.database.entity.asSyncedExpense
import com.spendlens.core.model.ExpenseConflict
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

/** Expenses in `CONFLICT`, and the user's decision on each. */
interface ExpenseConflictRepository {
    /** Both versions of [expenseId], or null once it is no longer in conflict — including right after a decision. */
    fun observeConflict(expenseId: String): Flow<ExpenseConflict?>

    /**
     * Every expense waiting for a decision. Includes ones deleted on this device, which the expense
     * list does not show — this is how they stay reachable.
     */
    fun observeConflictedExpenseIds(): Flow<List<String>>

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
        private val syncManager: SyncManager,
        private val clock: Clock,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : ExpenseConflictRepository {
        override fun observeConflict(expenseId: String): Flow<ExpenseConflict?> =
            combine(
                expenseDao.observeExpenseIncludingDeleted(expenseId),
                conflictDao.observeConflict(expenseId),
            ) { local, remote ->
                if (local == null || remote == null) {
                    null
                } else {
                    ExpenseConflict(
                        local = local.asDomainModel(),
                        // The server never has a photo; the local one belongs to either version.
                        remote = remote.asSyncedExpense(receiptImagePath = local.receiptImagePath).asDomainModel(),
                    )
                }
            }

        override fun observeConflictedExpenseIds(): Flow<List<String>> = conflictDao.observeConflictedExpenseIds()

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
                // Keeping this device's version is an edit the server has not seen yet.
                syncManager.requestSync()
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
