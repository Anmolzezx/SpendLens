package com.spendlens.server

import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/** Storage behaviour the protocol suite cannot see from outside: restarts, and concurrent writers. */
class SqliteExpenseStoreTest {
    private val database = File.createTempFile("spendlens-server", ".db")

    @After
    fun tearDown() {
        listOf("", "-wal", "-shm").forEach { File(database.path + it).delete() }
    }

    /**
     * If versions restarted at 1 after a restart, every device's cursor would be ahead of the server,
     * and none would ever download a change again.
     */
    @Test
    fun `data and the version counter survive a restart`() =
        runTest {
            val before = SqliteExpenseStore(database.path).use { store ->
                store.push(listOf(NetworkPushRequest(expense("a"), baseVersion = null))).single().version()
            }

            SqliteExpenseStore(database.path).use { store ->
                assertEquals(listOf("a"), store.changes(since = 0, limit = 10).changes.map { it.id })
                val after = store.push(listOf(NetworkPushRequest(expense("b"), baseVersion = null))).single().version()
                assertEquals(before + 1, after)
            }
        }

    /** Two devices uploading their first copy of one expense at the same moment: exactly one may win. */
    @Test
    fun `concurrent writes to one expense cannot both be accepted`() =
        runTest {
            SqliteExpenseStore(database.path).use { store ->
                val results = (1..20)
                    .map { device ->
                        async {
                            store
                                .push(listOf(NetworkPushRequest(expense("a").copy(merchant = "Device $device"), null)))
                                .single()
                        }
                    }.awaitAll()

                assertEquals(1, results.count { it is NetworkPushResult.Accepted })
            }
        }

    @Test
    fun `the server will not start without a token`() {
        assertThrows(IllegalArgumentException::class.java) { ServerConfig.fromEnvironment(emptyMap()) }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { ServerConfig.fromEnvironment(mapOf("SPENDLENS_TOKEN" to " ")) }
    }

    private fun NetworkPushResult.version() = (this as NetworkPushResult.Accepted).version
}
