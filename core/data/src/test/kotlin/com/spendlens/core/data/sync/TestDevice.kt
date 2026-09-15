package com.spendlens.core.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendlens.core.data.repository.OfflineFirstExpenseConflictRepository
import com.spendlens.core.data.repository.OfflineFirstExpenseRepository
import com.spendlens.core.database.RoomTransactionRunner
import com.spendlens.core.database.SpendLensDatabase
import com.spendlens.core.database.entity.ExpenseConflictEntity
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.network.FakeSpendLensServer
import com.spendlens.core.testing.sync.TestSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One simulated phone or tablet: its own database, clock and repositories, talking to a server that
 * other devices share. Everything below the network is the production code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TestDevice(
    server: FakeSpendLensServer,
) : AutoCloseable {
    private val database = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpendLensDatabase::class.java)
        .build()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TickingClock(Instant.parse("2026-09-15T08:00:00Z"))
    private val transaction = RoomTransactionRunner(database)

    /** Records requests only. These tests decide exactly when each device syncs. */
    val syncManager = TestSyncManager()

    val expenses = OfflineFirstExpenseRepository(database.expenseDao(), transaction, syncManager, clock, dispatcher)

    val conflicts = OfflineFirstExpenseConflictRepository(
        expenseDao = database.expenseDao(),
        conflictDao = database.expenseConflictDao(),
        transaction = transaction,
        syncManager = syncManager,
        clock = clock,
        ioDispatcher = dispatcher,
    )

    private val synchronizer = ExpenseSynchronizer(
        expenseDao = database.expenseDao(),
        conflictDao = database.expenseConflictDao(),
        cursorDao = database.syncCursorDao(),
        transaction = transaction,
        network = server,
        ioDispatcher = dispatcher,
    )

    suspend fun sync(): SyncReport = synchronizer.sync()

    /**
     * Loads the expense, changes it, and saves it the way the edit screen does — which builds a fresh
     * `Expense` from its form and so never carries a `remoteVersion`. Passing the loaded one straight
     * back would hide a repository that trusted the caller's sync bookkeeping.
     */
    suspend fun edit(
        id: String,
        change: (Expense) -> Expense,
    ) {
        val current = checkNotNull(expenses.observeExpense(id).first()) { "No visible expense $id" }
        expenses.upsert(change(current).copy(remoteVersion = null))
    }

    /** The raw row, tombstones and sync bookkeeping included. */
    suspend fun stored(id: String): ExpenseEntity? = database.expenseDao().getExpense(id)

    suspend fun storedConflict(id: String): ExpenseConflictEntity? = database.expenseConflictDao().getConflict(id)

    override fun close() = database.close()
}

internal fun expense(
    id: String,
    merchant: String = "Blue Bottle",
    amountMinor: Long = 650,
    receiptImagePath: String? = null,
) = Expense(
    id = id,
    merchant = merchant,
    amountMinor = amountMinor,
    currency = "USD",
    occurredOn = LocalDate.parse("2026-09-15"),
    categoryId = "cat-dining",
    note = null,
    receiptImagePath = receiptImagePath,
    syncState = SyncState.PENDING,
    updatedAt = Instant.EPOCH,
    isDeleted = false,
)

/**
 * Advances a second on every read, so each save gets a distinct `updatedAt` — which is what lets sync
 * tell "unchanged since upload" from "edited again" — while staying fully deterministic.
 */
private class TickingClock(
    private var now: Instant,
) : Clock() {
    override fun instant(): Instant = now.also { now = now.plusSeconds(1) }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
