package com.spendlens.core.data.sync

import com.spendlens.core.model.Expense
import com.spendlens.core.model.ExpenseField
import com.spendlens.core.model.SyncState
import com.spendlens.core.testing.network.FakeSpendLensServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The same expense changed on two devices while they could not see each other's edit.
 *
 * The property under test throughout: **no edit is ever silently discarded.** Last-write-wins would
 * pass a weaker version of every test here while losing one side's change without telling anyone.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseConflictTest {
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

    // -- detection ----------------------------------------------------------------------------------

    @Test
    fun `the same expense edited on two devices becomes a conflict instead of losing an edit`() =
        runTest {
            bothDevicesHave(expense("a", amountMinor = 650))
            phone.edit("a") { it.copy(amountMinor = 700) }
            tablet.edit("a") { it.copy(amountMinor = 900) }

            phone.sync()
            val report = tablet.sync()

            assertEquals(1, report.conflicts)
            // The phone synced first; its edit is on the server, untouched.
            assertEquals(700L, server.expenses.getValue("a").amountMinor)
            // The tablet keeps showing what its user typed...
            assertEquals(900L, tablet.stored("a")?.amountMinor)
            assertEquals(SyncState.CONFLICT, tablet.stored("a")?.syncState)
            // ...and holds the phone's version for the user to compare.
            assertEquals(700L, tablet.storedConflict("a")?.amountMinor)
            assertEquals(server.expenses.getValue("a").version, tablet.storedConflict("a")?.serverVersion)
        }

    @Test
    fun `the same edit made on both devices is not a conflict`() =
        runTest {
            bothDevicesHave(expense("a", amountMinor = 650))
            phone.edit("a") { it.copy(amountMinor = 700) }
            tablet.edit("a") { it.copy(amountMinor = 700) }

            phone.sync()

            assertEquals(0, tablet.sync().conflicts)
            assertEquals(SyncState.SYNCED, tablet.stored("a")?.syncState)
            assertEquals(server.expenses.getValue("a").version, tablet.stored("a")?.remoteVersion)
        }

    @Test
    fun `deleting on both devices is not a conflict`() =
        runTest {
            bothDevicesHave(expense("a"))
            phone.edit("a") { it.copy(merchant = "Edited, then deleted") }
            phone.expenses.delete("a")
            tablet.expenses.delete("a")

            phone.sync()

            assertEquals(0, tablet.sync().conflicts)
            assertEquals(SyncState.SYNCED, tablet.stored("a")?.syncState)
        }

    @Test
    fun `edited here and deleted there is a conflict`() =
        runTest {
            bothDevicesHave(expense("a"))
            phone.expenses.delete("a")
            tablet.edit("a") { it.copy(merchant = "Still wanted") }

            phone.sync()
            tablet.sync()

            assertEquals(SyncState.CONFLICT, tablet.stored("a")?.syncState)
            assertEquals(false, tablet.stored("a")?.isDeleted)
            assertEquals(true, tablet.storedConflict("a")?.isDeleted)
        }

    @Test
    fun `a conflicted expense is not uploaded until the user decides`() =
        runTest {
            conflictOnTablet()

            assertEquals(0, tablet.sync().pushed)
            assertEquals(700L, server.expenses.getValue("a").amountMinor)
        }

    /**
     * Detected on the pull rather than the push: the user edits after this sync's upload has already
     * gone, just as another device's change to the same expense lands.
     */
    @Test
    fun `an expense edited mid-sync while another device changes it becomes a conflict`() =
        runTest {
            bothDevicesHave(expense("a", amountMinor = 650), expense("b"))
            phone.edit("b") { it.copy(merchant = "Something to upload") }
            server.beforePushResponse = {
                server.beforePushResponse = {}
                phone.edit("a") { it.copy(amountMinor = 111) }
                tablet.edit("a") { it.copy(amountMinor = 222) }
                tablet.sync()
            }

            assertEquals(1, phone.sync().conflicts)

            assertEquals(111L, phone.stored("a")?.amountMinor)
            assertEquals(SyncState.CONFLICT, phone.stored("a")?.syncState)
            assertEquals(222L, phone.storedConflict("a")?.amountMinor)
        }

    // -- what the conflict screen sees ---------------------------------------------------------------

    @Test
    fun `a conflict is observed with both versions`() =
        runTest {
            conflictOnTablet(tabletReceipt = "receipts/a.jpg")

            val conflict = tablet.conflicts.observeConflict("a").first()

            assertEquals(900L, conflict?.local?.amountMinor)
            assertEquals(700L, conflict?.remote?.amountMinor)
            assertEquals(setOf(ExpenseField.AMOUNT), conflict?.differences)
            // The photo is on this device whichever version is kept.
            assertEquals("receipts/a.jpg", conflict?.remote?.receiptImagePath)
            assertEquals(listOf("a"), tablet.conflicts.observeConflictedExpenseIds().first())
        }

    /** The list hides deleted expenses, so this conflict is reachable only through the conflict ids. */
    @Test
    fun `an expense deleted here but edited there is still observable`() =
        runTest {
            bothDevicesHave(expense("a"))
            phone.edit("a") { it.copy(merchant = "Still wanted") }
            tablet.expenses.delete("a")
            phone.sync()
            tablet.sync()

            assertEquals(emptyList<Expense>(), tablet.expenses.observeExpenses().first())
            assertEquals(listOf("a"), tablet.conflicts.observeConflictedExpenseIds().first())
            val conflict = tablet.conflicts.observeConflict("a").first()
            assertEquals(true, conflict?.local?.isDeleted)
            assertEquals("Still wanted", conflict?.remote?.merchant)
        }

    @Test
    fun `a decision clears the conflict for anyone observing it`() =
        runTest {
            conflictOnTablet()

            tablet.conflicts.keepRemote("a")

            assertNull(tablet.conflicts.observeConflict("a").first())
            assertEquals(emptyList<String>(), tablet.conflicts.observeConflictedExpenseIds().first())
        }

    // -- resolution ---------------------------------------------------------------------------------

    @Test
    fun `keeping this device's version uploads it and reaches the other device`() =
        runTest {
            conflictOnTablet()

            val requestsBefore = tablet.syncManager.requestCount
            tablet.conflicts.keepLocal("a")

            assertNull(tablet.storedConflict("a"))
            assertEquals(requestsBefore + 1, tablet.syncManager.requestCount)
            assertEquals(SyncReport(pushed = 1), tablet.sync())
            assertEquals(900L, server.expenses.getValue("a").amountMinor)
            phone.sync()
            assertEquals(900L, phone.stored("a")?.amountMinor)
        }

    @Test
    fun `keeping the other device's version replaces this one's`() =
        runTest {
            conflictOnTablet()
            val serverVersion = server.expenses.getValue("a").version

            tablet.conflicts.keepRemote("a")

            assertNull(tablet.storedConflict("a"))
            assertEquals(700L, tablet.stored("a")?.amountMinor)
            assertEquals(SyncState.SYNCED, tablet.stored("a")?.syncState)
            // Nothing to upload, and the server is not written to.
            assertEquals(SyncReport(), tablet.sync())
            assertEquals(serverVersion, server.expenses.getValue("a").version)
        }

    @Test
    fun `keeping the other device's version keeps this device's receipt photo`() =
        runTest {
            conflictOnTablet(tabletReceipt = "receipts/a.jpg")

            tablet.conflicts.keepRemote("a")

            assertEquals("receipts/a.jpg", tablet.stored("a")?.receiptImagePath)
        }

    @Test
    fun `if the other device edits again before the user decides, the newest version is shown`() =
        runTest {
            conflictOnTablet()

            phone.edit("a") { it.copy(amountMinor = 800) }
            phone.sync()
            tablet.sync()

            assertEquals(800L, tablet.storedConflict("a")?.amountMinor)
            assertEquals(900L, tablet.stored("a")?.amountMinor)

            // Keeping local now re-bases onto the latest server version, so the upload is accepted.
            tablet.conflicts.keepLocal("a")
            assertEquals(SyncReport(pushed = 1), tablet.sync())
        }

    @Test
    fun `editing a conflicted expense changes this device's side without settling it`() =
        runTest {
            conflictOnTablet()

            tablet.edit("a") { it.copy(amountMinor = 950) }

            assertEquals(SyncState.CONFLICT, tablet.stored("a")?.syncState)
            assertEquals(700L, tablet.storedConflict("a")?.amountMinor)
            tablet.conflicts.keepLocal("a")
            tablet.sync()
            assertEquals(950L, server.expenses.getValue("a").amountMinor)
        }

    // -- setup --------------------------------------------------------------------------------------

    private suspend fun bothDevicesHave(vararg expenses: Expense) {
        expenses.forEach { phone.expenses.upsert(it) }
        phone.sync()
        tablet.sync()
    }

    /** Phone set 650 → 700 and synced first; tablet set 650 → 900 and hit the conflict. */
    private suspend fun conflictOnTablet(tabletReceipt: String? = null) {
        bothDevicesHave(expense("a", amountMinor = 650))
        phone.edit("a") { it.copy(amountMinor = 700) }
        tablet.edit("a") { it.copy(amountMinor = 900, receiptImagePath = tabletReceipt) }
        phone.sync()
        tablet.sync()
        check(tablet.stored("a")?.syncState == SyncState.CONFLICT) { "Setup did not produce a conflict" }
    }
}
