package com.spendlens.server

import com.spendlens.core.protocol.NetworkChangePage
import com.spendlens.core.protocol.NetworkExpense
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import com.spendlens.core.protocol.SpendLensJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/** Where the server keeps expenses. The protocol's rules live here; the HTTP layer only translates. */
interface ExpenseStore {
    suspend fun push(requests: List<NetworkPushRequest>): List<NetworkPushResult>

    suspend fun changes(
        since: Long,
        limit: Int,
    ): NetworkChangePage
}

/**
 * [ExpenseStore] on SQLite, over plain JDBC.
 *
 * Each row keeps an expense's latest version only, so a pull sends current state rather than a log
 * of every edit. Deletions are ordinary rows with `is_deleted` set — a device offline for a month
 * still has to learn about them.
 *
 * **The version counter is `MAX(version) + 1`.** Rows are replaced, never removed, so the maximum can
 * only grow; there is no separate counter to fall out of step with the data after a crash.
 *
 * **One connection, one writer at a time.** A push reads each record's version and writes on the
 * strength of it, so two pushes interleaving could both pass the check. The mutex plus a transaction
 * make each batch atomic. SQLite allows one writer anyway; a personal expense tracker has no load to
 * spread.
 */
class SqliteExpenseStore(
    path: String,
) : ExpenseStore,
    AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")
    private val mutex = Mutex()

    init {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT PRIMARY KEY NOT NULL,
                    version INTEGER NOT NULL UNIQUE,
                    body TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override suspend fun push(requests: List<NetworkPushRequest>): List<NetworkPushResult> =
        locked {
            transaction { requests.map(::write) }
        }

    override suspend fun changes(
        since: Long,
        limit: Int,
    ): NetworkChangePage =
        locked {
            // One extra row answers "is there more?" without a second query.
            val rows = connection
                .prepareStatement("SELECT body FROM expenses WHERE version > ? ORDER BY version LIMIT ?")
                .use { statement ->
                    statement.bind(since, limit + 1).executeQuery().use { result ->
                        buildList { while (result.next()) add(decode(result.getString(1))) }
                    }
                }
            val page = rows.take(limit)
            NetworkChangePage(
                changes = page,
                nextCursor = page.lastOrNull()?.version ?: since,
                hasMore = rows.size > limit,
            )
        }

    override fun close() = connection.close()

    /** The compare-and-set. Must run inside [transaction]. */
    private fun write(request: NetworkPushRequest): NetworkPushResult {
        val id = request.expense.id
        val current = find(id)
        if (current != null && current.version != request.baseVersion) {
            return NetworkPushResult.Conflict(current)
        }
        val version = nextVersion()
        connection
            .prepareStatement(
                """
                INSERT INTO expenses (id, version, body) VALUES (?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET version = excluded.version, body = excluded.body
                """.trimIndent(),
            ).use { statement ->
                statement.bind(id, version, SpendLensJson.encodeToString(request.expense.copy(version = version)))
                statement.executeUpdate()
            }
        return NetworkPushResult.Accepted(id = id, version = version)
    }

    private fun find(id: String): NetworkExpense? =
        connection.prepareStatement("SELECT body FROM expenses WHERE id = ?").use { statement ->
            statement.bind(id).executeQuery().use { result -> if (result.next()) decode(result.getString(1)) else null }
        }

    private fun nextVersion(): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COALESCE(MAX(version), 0) + 1 FROM expenses").use { result ->
                result.next()
                result.getLong(1)
            }
        }

    /** Binds [values] to the statement's `?` placeholders in order. */
    private fun PreparedStatement.bind(vararg values: Any): PreparedStatement =
        apply { values.forEachIndexed { index, value -> setObject(index + 1, value) } }

    private fun decode(body: String): NetworkExpense = SpendLensJson.decodeFromString(body)

    /** JDBC blocks, so it runs on the IO pool, and one caller at a time. */
    private suspend fun <T> locked(block: () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }

    /** Commits if [block] returns; rolls back if it throws, whatever it throws. */
    private fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        var committed = false
        try {
            return block().also {
                connection.commit()
                committed = true
            }
        } finally {
            if (!committed) connection.rollback()
            connection.autoCommit = true
        }
    }
}
