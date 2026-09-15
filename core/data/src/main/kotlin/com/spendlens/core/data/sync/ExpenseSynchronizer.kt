package com.spendlens.core.data.sync

import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.database.DatabaseTransactionRunner
import com.spendlens.core.database.dao.ExpenseConflictDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.dao.SyncCursorDao
import com.spendlens.core.database.entity.SyncCursorEntity
import com.spendlens.core.model.SyncState
import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.protocol.NetworkExpense
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

/**
 * Two-way sync of expenses: upload this device's changes, then download everyone else's.
 *
 * Conflicts are **detected, never silently resolved**. When the same expense has changed here and on
 * the server since the two last agreed, neither edit is thrown away: this device keeps its own
 * version in `expenses`, the server's goes into `expense_conflicts`, and the user chooses. See
 * [SpendLensNetworkDataSource] for the protocol that makes that detectable.
 *
 * Network failures propagate as exceptions. Nothing is marked synced until the server has answered,
 * so a sync cut off at any point is safe to simply run again — deciding when is the caller's job.
 *
 * Passes run **one at a time**. The periodic sync and a sync requested after a save are separate
 * WorkManager jobs and can start together; interleaved, both would upload the same rows, and the
 * slower one would write back an older pull cursor. The lock is per instance, and the app binds one.
 */
class ExpenseSynchronizer
    @Inject
    constructor(
        private val expenseDao: ExpenseDao,
        private val conflictDao: ExpenseConflictDao,
        private val cursorDao: SyncCursorDao,
        private val transaction: DatabaseTransactionRunner,
        private val network: SpendLensNetworkDataSource,
        private val clock: Clock,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) : Synchronizer {
        private val mutex = Mutex()

        /** Push before pull, so this device's changes are on the server before it compares against it. */
        override suspend fun sync(): SyncReport =
            mutex.withLock {
                withContext(ioDispatcher) {
                    push() + pull()
                }
            }

        private suspend fun push(): SyncReport {
            var report = SyncReport()
            expenseDao.getPendingSync().chunked(PUSH_BATCH_SIZE).forEach { batch ->
                val sent = batch.associateBy { it.id }
                val results = network.pushExpenses(
                    batch.map { NetworkPushRequest(expense = it.asNetworkExpense(), baseVersion = it.remoteVersion) },
                )
                results.forEach { result ->
                    report += when (result) {
                        is NetworkPushResult.Accepted -> {
                            val pushed = checkNotNull(sent[result.id]) { "Server answered for unsent ${result.id}" }
                            expenseDao.markPushed(
                                id = result.id,
                                version = result.version,
                                pushedUpdatedAt = pushed.updatedAt,
                            )
                            SyncReport(pushed = 1)
                        }
                        // The server refused because it holds a newer version. That is the same
                        // situation as pulling one, so it goes through the same decision.
                        is NetworkPushResult.Conflict -> transaction { applyRemote(result.current) }
                    }
                }
            }
            return report
        }

        private suspend fun pull(): SyncReport {
            var report = SyncReport()
            val previous = cursorDao.getSyncCursor(EXPENSES_SYNC_STREAM)
            var cursor = previous?.cursor ?: 0L
            do {
                val page = network.pullExpenses(since = cursor, limit = PULL_PAGE_SIZE)
                // The page and the cursor commit together. A crash can lose the whole page, which
                // the next sync downloads again, but never the page without the cursor or the reverse.
                transaction {
                    page.changes.forEach { report += applyRemote(it) }
                    cursorDao.upsert(
                        SyncCursorEntity(
                            stream = EXPENSES_SYNC_STREAM,
                            cursor = page.nextCursor,
                            // Stamped only with the final page: before that, this device is not yet up to
                            // date, and "last synced" must not say it is.
                            lastSyncedAt = if (page.hasMore) previous?.lastSyncedAt else clock.instant(),
                        ),
                    )
                }
                cursor = page.nextCursor
            } while (page.hasMore)
            return report
        }

        /**
         * Brings one server record into the local database. Must run inside a transaction: it decides
         * based on the local row, and a user edit landing between the read and the write would be lost.
         */
        private suspend fun applyRemote(remote: NetworkExpense): SyncReport {
            val version = remote.serverVersion()
            val local = expenseDao.getExpense(remote.id)
            return when {
                // A deletion of something this device never had: nothing to delete.
                local == null && remote.isDeleted -> SyncReport()

                local == null -> {
                    expenseDao.upsert(remote.asSyncedEntity(receiptImagePath = null))
                    SyncReport(pulled = 1)
                }

                // Already seen — typically this device's own upload, coming back on the pull.
                version <= (local.remoteVersion ?: NEVER_SYNCED) -> SyncReport()

                // Unchanged here, so the server's version simply wins.
                local.syncState == SyncState.SYNCED -> {
                    expenseDao.upsert(remote.asSyncedEntity(receiptImagePath = local.receiptImagePath))
                    SyncReport(pulled = 1)
                }

                local.syncState == SyncState.CONFLICT -> refreshConflict(remote, version)

                // PENDING: changed here and on the server. If both made the same change — or a push
                // was accepted but its response lost — there is nothing to choose between.
                local.hasSameContentAs(remote) -> {
                    expenseDao.upsert(remote.asSyncedEntity(receiptImagePath = local.receiptImagePath))
                    SyncReport(pulled = 1)
                }

                else -> {
                    expenseDao.upsert(local.copy(syncState = SyncState.CONFLICT))
                    conflictDao.upsert(remote.asConflictEntity())
                    SyncReport(conflicts = 1)
                }
            }
        }

        /**
         * The user has not decided yet. If the other side has moved on since, show them its latest.
         *
         * A conflict found by an upload is seen again by the pull that follows, at the same version —
         * which is not news, and must not be counted twice.
         */
        private suspend fun refreshConflict(
            remote: NetworkExpense,
            version: Long,
        ): SyncReport {
            val known = conflictDao.getConflict(remote.id)
            if (known != null && version <= known.serverVersion) return SyncReport()
            conflictDao.upsert(remote.asConflictEntity())
            return SyncReport(conflicts = 1)
        }

        private companion object {
            /** Below every real version, which starts at 1. */
            const val NEVER_SYNCED = 0L
            const val PUSH_BATCH_SIZE = 50
            const val PULL_PAGE_SIZE = 100
        }
    }
