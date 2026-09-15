package com.spendlens.core.testing.network

import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.network.model.NetworkChangePage
import com.spendlens.core.network.model.NetworkExpense
import com.spendlens.core.network.model.NetworkPushRequest
import com.spendlens.core.network.model.NetworkPushResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * An in-memory server that implements the sync protocol faithfully.
 *
 * Several simulated devices can share one instance, which is how a test edits the same expense on a
 * phone and a tablet. The hooks below reproduce the failures that make sync hard — a dropped response,
 * an edit landing mid-request — deterministically, which a real server cannot.
 */
class FakeSpendLensServer : SpendLensNetworkDataSource {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, NetworkExpense>()
    private var lastVersion = 0L

    /** What the server holds now, by id. */
    val expenses: Map<String, NetworkExpense> get() = records.toMap()

    /** Every `since` a device has pulled from, in order. */
    val pullCursors = mutableListOf<Long>()

    /** Caps page size below what the client asks for, as real servers do, to exercise paging. */
    var maxPageSize: Int = Int.MAX_VALUE

    /** When set, the next request of either kind fails before the server does anything. */
    var failNextRequest = false

    /** When set, the next push is **applied** but its response never arrives. */
    var loseNextPushResponse = false

    /**
     * Runs after a push is applied and before it is answered: the moment a device's database can
     * change underneath a sync that is still waiting on the network.
     */
    var beforePushResponse: suspend () -> Unit = {}

    override suspend fun pushExpenses(requests: List<NetworkPushRequest>): List<NetworkPushResult> {
        val results = mutex.withLock {
            failIfRequested()
            requests.map { write(it.expense, it.baseVersion) }
        }
        beforePushResponse()
        if (loseNextPushResponse) {
            loseNextPushResponse = false
            throw IOException("Connection reset before the response arrived")
        }
        return results
    }

    override suspend fun pullExpenses(
        since: Long,
        limit: Int,
    ): NetworkChangePage =
        mutex.withLock {
            failIfRequested()
            pullCursors += since
            // Every stored record has a version; write() assigns one before storing.
            val newer = records.values.filter { (it.version ?: 0) > since }.sortedBy { it.version }
            val page = newer.take(minOf(limit, maxPageSize))
            NetworkChangePage(
                changes = page,
                nextCursor = page.lastOrNull()?.version ?: since,
                hasMore = newer.size > page.size,
            )
        }

    /** A write from a client this test does not otherwise model, based on whatever the server holds. */
    suspend fun writeFromAnotherDevice(expense: NetworkExpense): Long =
        mutex.withLock {
            val result = write(expense, baseVersion = records[expense.id]?.version)
            (result as NetworkPushResult.Accepted).version
        }

    private fun write(
        expense: NetworkExpense,
        baseVersion: Long?,
    ): NetworkPushResult {
        val current = records[expense.id]
        if (current != null && current.version != baseVersion) {
            return NetworkPushResult.Conflict(current)
        }
        val version = ++lastVersion
        records[expense.id] = expense.copy(version = version)
        return NetworkPushResult.Accepted(id = expense.id, version = version)
    }

    private fun failIfRequested() {
        if (failNextRequest) {
            failNextRequest = false
            throw IOException("Network unavailable")
        }
    }
}
