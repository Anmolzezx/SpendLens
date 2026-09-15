package com.spendlens.core.testing.network

import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.protocol.NetworkChangePage
import com.spendlens.core.protocol.NetworkExpense
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The sync protocol's rules, written once as tests that any implementation must pass.
 *
 * Subclassed twice: by [FakeSpendLensServer], and by the real Ktor server reached over HTTP through
 * Retrofit. The sync engine's two-device tests all run against the fake, so they are only as good as
 * the fake is faithful. This suite is what makes that more than a hope — a rule the fake got wrong fails
 * here, next to the same test passing against production.
 *
 * Everything is set up through the protocol itself, never through a back door into either server.
 */
abstract class SyncProtocolContract {
    /** A new, empty server. Called before every test. */
    protected abstract fun newServer(): SpendLensNetworkDataSource

    private lateinit var server: SpendLensNetworkDataSource

    @Before
    fun createServer() {
        server = newServer()
    }

    // -- push ---------------------------------------------------------------------------------------

    @Test
    fun `a new expense is accepted with a version`() =
        runTest {
            val result = server.pushExpenses(listOf(NetworkPushRequest(expense("a"), baseVersion = null))).single()

            assertTrue("Expected Accepted, got $result", result is NetworkPushResult.Accepted)
            assertEquals("a", result.id)
        }

    @Test
    fun `every accepted write gets a higher version than the one before`() =
        runTest {
            val first = accept(expense("a"))
            val second = accept(expense("b"))
            val third = accept(expense("a", merchant = "Edited"), baseVersion = first)

            assertTrue("$first < $second < $third", first < second && second < third)
        }

    @Test
    fun `an edit based on an older version is refused with the server's copy`() =
        runTest {
            val first = accept(expense("a", merchant = "First"))
            val second = accept(expense("a", merchant = "Second"), baseVersion = first)

            val result = push(expense("a", merchant = "Third"), baseVersion = first)

            assertTrue("Expected Conflict, got $result", result is NetworkPushResult.Conflict)
            val current = (result as NetworkPushResult.Conflict).current
            assertEquals("Second", current.merchant)
            assertEquals(second, current.version)
            // Refused means not written.
            assertEquals("Second", pullAll().single().merchant)
        }

    /** Also how a retry after a lost response looks: the device's first attempt already landed. */
    @Test
    fun `an expense the server already has is refused when pushed as new`() =
        runTest {
            accept(expense("a"))

            assertTrue(push(expense("a"), baseVersion = null) is NetworkPushResult.Conflict)
        }

    @Test
    fun `each write in a batch is decided on its own`() =
        runTest {
            accept(expense("a"))

            val results = server
                .pushExpenses(
                    listOf(
                        NetworkPushRequest(expense("a", merchant = "Stale"), baseVersion = null),
                        NetworkPushRequest(expense("b"), baseVersion = null),
                    ),
                ).associateBy { it.id }

            assertTrue(results.getValue("a") is NetworkPushResult.Conflict)
            assertTrue(results.getValue("b") is NetworkPushResult.Accepted)
        }

    // -- pull ---------------------------------------------------------------------------------------

    @Test
    fun `a pulled expense carries exactly what was pushed`() =
        runTest {
            val pushed = expense("a", merchant = "Blue Bottle").copy(note = "Oat flat white")
            val version = accept(pushed)

            assertEquals(pushed.copy(version = version), pullAll().single())
        }

    @Test
    fun `a pull returns only changes after the cursor, oldest first`() =
        runTest {
            val a = accept(expense("a"))
            accept(expense("b"))
            val c = accept(expense("c"))

            val page = server.pullExpenses(since = a, limit = 10)

            assertEquals(listOf("b", "c"), page.changes.map { it.id })
            assertEquals(c, page.nextCursor)
            assertFalse(page.hasMore)
        }

    @Test
    fun `an expense edited twice is pulled once, at its latest version`() =
        runTest {
            val first = accept(expense("a", merchant = "First"))
            val second = accept(expense("a", merchant = "Second"), baseVersion = first)

            val pulled = pullAll().single()
            assertEquals("Second", pulled.merchant)
            assertEquals(second, pulled.version)
        }

    @Test
    fun `pages follow the limit and say whether more remain`() =
        runTest {
            listOf("a", "b", "c").forEach { accept(expense(it)) }

            val first = server.pullExpenses(since = 0, limit = 2)
            val second = server.pullExpenses(since = first.nextCursor, limit = 2)
            val third = server.pullExpenses(since = second.nextCursor, limit = 2)

            assertEquals(listOf("a", "b"), first.changes.map { it.id })
            assertTrue(first.hasMore)
            assertEquals(listOf("c"), second.changes.map { it.id })
            assertFalse(second.hasMore)
            // Nothing new: the cursor stays where it is, so the next pull asks the same question.
            assertEquals(NetworkChangePage(emptyList(), nextCursor = second.nextCursor, hasMore = false), third)
        }

    @Test
    fun `a deletion is kept and pulled like any other change`() =
        runTest {
            val first = accept(expense("a"))
            accept(expense("a").copy(isDeleted = true), baseVersion = first)

            assertTrue(pullAll().single().isDeleted)
        }

    // -- helpers ------------------------------------------------------------------------------------

    private suspend fun push(
        expense: NetworkExpense,
        baseVersion: Long? = null,
    ): NetworkPushResult = server.pushExpenses(listOf(NetworkPushRequest(expense, baseVersion))).single()

    private suspend fun accept(
        expense: NetworkExpense,
        baseVersion: Long? = null,
    ): Long =
        when (val result = push(expense, baseVersion)) {
            is NetworkPushResult.Accepted -> result.version
            is NetworkPushResult.Conflict -> error("Setup write for ${expense.id} was refused: $result")
        }

    private suspend fun pullAll(): List<NetworkExpense> = server.pullExpenses(since = 0, limit = PAGE).changes

    private fun expense(
        id: String,
        merchant: String = "Merchant",
    ) = NetworkExpense(
        id = id,
        merchant = merchant,
        amountMinor = 1_234,
        currency = "USD",
        occurredOn = "2026-09-15",
        categoryId = "cat-dining",
        updatedAt = "2026-09-15T08:00:00Z",
    )

    private companion object {
        const val PAGE = 100
    }
}
