package com.spendlens.core.data.sync

import com.spendlens.core.model.SyncState
import com.spendlens.core.network.model.NetworkExpense
import com.spendlens.core.testing.network.FakeSpendLensServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Upload and download, including the failures in between: a response that never arrives, an edit
 * saved while a request is in flight, a server that pages its answers.
 *
 * Conflicts have their own suite, [ExpenseConflictTest].
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseSynchronizerTest {
    private val server = FakeSpendLensServer()
    private lateinit var phone: TestDevice
    private lateinit var tablet: TestDevice

    @Before
    fun setUp() {
        phone = TestDevice(server)
        tablet = TestDevice(server)
    }

    @After
    fun tearDown() {
        phone.close()
        tablet.close()
    }

    // -- upload -------------------------------------------------------------------------------------

    @Test
    fun `uploads a new expense and marks it synced at the server's version`() =
        runTest {
            phone.expenses.upsert(expense("a", merchant = "Blue Bottle"))

            // Pulled 0: the pull brings back this device's own upload, which it already has.
            assertEquals(SyncReport(pushed = 1), phone.sync())

            val onServer = server.expenses.getValue("a")
            assertEquals("Blue Bottle", onServer.merchant)
            assertEquals(SyncState.SYNCED, phone.stored("a")?.syncState)
            assertEquals(onServer.version, phone.stored("a")?.remoteVersion)
        }

    @Test
    fun `uploads a deletion`() =
        runTest {
            phone.expenses.upsert(expense("a"))
            phone.sync()

            phone.expenses.delete("a")
            phone.sync()

            assertTrue(server.expenses.getValue("a").isDeleted)
            assertEquals(SyncState.SYNCED, phone.stored("a")?.syncState)
        }

    @Test
    fun `an edit saved while its upload is in flight goes up next time instead of being lost`() =
        runTest {
            phone.expenses.upsert(expense("a", merchant = "First"))
            server.beforePushResponse = {
                server.beforePushResponse = {}
                phone.edit("a") { it.copy(merchant = "Second") }
            }

            phone.sync()

            // The server has the first save. The second must not be marked synced along with it.
            assertEquals("First", server.expenses.getValue("a").merchant)
            assertEquals(SyncState.PENDING, phone.stored("a")?.syncState)

            // And it uploads cleanly, not as a conflict with this device's own first save.
            assertEquals(SyncReport(pushed = 1), phone.sync())
            assertEquals("Second", server.expenses.getValue("a").merchant)
        }

    /** The server wrote it; the phone never heard. Retrying must not report a conflict with itself. */
    @Test
    fun `a lost upload response is not mistaken for a conflict when retried`() =
        runTest {
            phone.expenses.upsert(expense("a"))
            server.loseNextPushResponse = true

            assertTrue(runCatching { phone.sync() }.exceptionOrNull() is IOException)
            assertEquals(SyncState.PENDING, phone.stored("a")?.syncState)

            assertEquals(0, phone.sync().conflicts)
            assertEquals(SyncState.SYNCED, phone.stored("a")?.syncState)
            assertEquals(server.expenses.getValue("a").version, phone.stored("a")?.remoteVersion)
        }

    @Test
    fun `a failed sync leaves changes pending, and they upload on retry`() =
        runTest {
            phone.expenses.upsert(expense("a"))
            server.failNextRequest = true

            assertTrue(runCatching { phone.sync() }.exceptionOrNull() is IOException)
            assertEquals(SyncState.PENDING, phone.stored("a")?.syncState)
            assertTrue(server.expenses.isEmpty())

            assertEquals(SyncReport(pushed = 1), phone.sync())
        }

    // -- download -----------------------------------------------------------------------------------

    @Test
    fun `downloads an expense created on another device`() =
        runTest {
            tablet.expenses.upsert(expense("a", merchant = "Blue Bottle"))
            tablet.sync()

            assertEquals(SyncReport(pulled = 1), phone.sync())

            assertEquals(
                listOf("Blue Bottle"),
                phone.expenses
                    .observeExpenses()
                    .first()
                    .map { it.merchant },
            )
            assertEquals(SyncState.SYNCED, phone.stored("a")?.syncState)
        }

    /** Photos never leave the device that took them, so taking the server's copy must keep this one's. */
    @Test
    fun `applies another device's edit and keeps this device's receipt photo`() =
        runTest {
            phone.expenses.upsert(expense("a", merchant = "Blue Bottle", receiptImagePath = "receipts/a.jpg"))
            phone.sync()
            tablet.sync()

            tablet.edit("a") { it.copy(merchant = "Blue Bottle Coffee") }
            tablet.sync()
            phone.sync()

            val stored = phone.stored("a")
            assertEquals("Blue Bottle Coffee", stored?.merchant)
            assertEquals("receipts/a.jpg", stored?.receiptImagePath)
            assertEquals(SyncState.SYNCED, stored?.syncState)
        }

    @Test
    fun `applies another device's deletion`() =
        runTest {
            phone.expenses.upsert(expense("a"))
            phone.sync()
            tablet.sync()

            tablet.expenses.delete("a")
            tablet.sync()
            phone.sync()

            assertTrue(
                phone.expenses
                    .observeExpenses()
                    .first()
                    .isEmpty(),
            )
            assertEquals(true, phone.stored("a")?.isDeleted)
        }

    @Test
    fun `ignores the deletion of an expense this device never had`() =
        runTest {
            server.writeFromAnotherDevice(networkExpense("gone", isDeleted = true))

            assertEquals(SyncReport(), phone.sync())

            assertNull(phone.stored("gone"))
        }

    @Test
    fun `follows the server's pages, then only asks for what is new`() =
        runTest {
            repeat(5) { tablet.expenses.upsert(expense("e$it")) }
            tablet.sync()
            server.maxPageSize = 2
            server.pullCursors.clear()

            assertEquals(5, phone.sync().pulled)
            assertEquals(listOf(0L, 2L, 4L), server.pullCursors)

            server.pullCursors.clear()
            assertEquals(SyncReport(), phone.sync())
            assertEquals(listOf(5L), server.pullCursors)
        }

    private fun networkExpense(
        id: String,
        isDeleted: Boolean = false,
    ) = NetworkExpense(
        id = id,
        merchant = "Somewhere",
        amountMinor = 100,
        currency = "USD",
        occurredOn = "2026-09-15",
        categoryId = "cat-other",
        isDeleted = isDeleted,
        updatedAt = "2026-09-15T08:00:00Z",
    )
}
