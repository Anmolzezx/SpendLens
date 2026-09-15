package com.spendlens.core.model.sample

import com.spendlens.core.model.Budget
import com.spendlens.core.model.Category
import com.spendlens.core.model.Expense
import com.spendlens.core.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fixtures for previews, screenshot tests, and the fake repository that backs the UI until the data
 * layer lands.
 *
 * Two things about this data are deliberate:
 *
 *  - **The dates are fixed, not relative to `now()`.** Paparazzi diffs pixels, so a fixture that says
 *    "three days ago" produces a different screenshot every day and every run fails. Everything here
 *    sits in August 2026. Several rows share a day on purpose, which is normal for a real ledger and
 *    exercises the secondary sort on `updatedAt`.
 *  - **The rows are awkward on purpose.** A 44-character merchant name, a $2,400 amount next to a
 *    $1.99 one, null notes, missing receipts, and all three sync states. Uniform, tidy fake data
 *    makes a broken layout look fine.
 */
private val AUGUST_2026: YearMonth = YearMonth.of(2026, 8)

object SampleCategories {
    val groceries = Category(id = "cat-groceries", name = "Groceries", colorIndex = 0, iconKey = "cart")
    val dining = Category(id = "cat-dining", name = "Dining Out", colorIndex = 1, iconKey = "restaurant")
    val transport = Category(id = "cat-transport", name = "Transport", colorIndex = 2, iconKey = "car")
    val utilities = Category(id = "cat-utilities", name = "Utilities", colorIndex = 3, iconKey = "bolt")
    val shopping = Category(id = "cat-shopping", name = "Shopping", colorIndex = 4, iconKey = "bag")
    val health = Category(id = "cat-health", name = "Health", colorIndex = 5, iconKey = "heart")

    val all: List<Category> = listOf(groceries, dining, transport, utilities, shopping, health)

    val byId: Map<String, Category> = all.associateBy(Category::id)
}

object SampleExpenses {
    /**
     * Visible expenses, tombstones excluded. Roughly newest-first, but **not** guaranteed sorted —
     * consumers sort explicitly, the same way the Room query will `ORDER BY occurred_at DESC`.
     * Relying on fixture order would hide a missing sort in the code under test.
     */
    val all: List<Expense> = listOf(
        expense(
            id = "exp-01",
            merchant = "Trader Joe's",
            amountMinor = 4_287,
            occurredOn = "2026-08-26",
            category = SampleCategories.groceries,
            receiptImagePath = "receipts/exp-01.jpg",
        ),
        expense(
            // 44 characters. If the list row truncates badly, it shows up here first.
            id = "exp-02",
            merchant = "Sri Venkateswara Supermarket & General Store",
            amountMinor = 12_650,
            occurredOn = "2026-08-25",
            category = SampleCategories.groceries,
            note = "Monthly stock-up",
            receiptImagePath = "receipts/exp-02.jpg",
            syncState = SyncState.PENDING,
            updatedAt = "2026-08-26T09:02:00Z",
        ),
        expense(
            id = "exp-03",
            merchant = "Blue Bottle Coffee",
            amountMinor = 675,
            occurredOn = "2026-08-25",
            category = SampleCategories.dining,
        ),
        expense(
            id = "exp-04",
            merchant = "Uber",
            amountMinor = 1_840,
            occurredOn = "2026-08-24",
            category = SampleCategories.transport,
        ),
        expense(
            id = "exp-05",
            merchant = "Con Edison",
            amountMinor = 14_320,
            occurredOn = "2026-08-24",
            category = SampleCategories.utilities,
            note = "August electricity",
        ),
        expense(
            // Largest amount, and the only conflicted row.
            id = "exp-06",
            merchant = "Amazon",
            amountMinor = 240_000,
            occurredOn = "2026-08-23",
            category = SampleCategories.shopping,
            note = "Standing desk + monitor arm",
            syncState = SyncState.CONFLICT,
            updatedAt = "2026-08-24T11:30:00Z",
        ),
        expense(
            id = "exp-07",
            merchant = "Shell",
            amountMinor = 5_210,
            occurredOn = "2026-08-22",
            category = SampleCategories.transport,
        ),
        expense(
            // Smallest amount.
            id = "exp-08",
            merchant = "CVS Pharmacy",
            amountMinor = 199,
            occurredOn = "2026-08-22",
            category = SampleCategories.health,
            syncState = SyncState.PENDING,
            updatedAt = "2026-08-22T19:05:00Z",
        ),
        expense(
            id = "exp-09",
            merchant = "Chipotle",
            amountMinor = 1_495,
            occurredOn = "2026-08-21",
            category = SampleCategories.dining,
        ),
        expense(
            id = "exp-10",
            merchant = "Spotify",
            amountMinor = 1_199,
            occurredOn = "2026-08-20",
            category = SampleCategories.shopping,
            note = "Monthly subscription",
        ),
        expense(
            id = "exp-11",
            merchant = "Whole Foods Market",
            amountMinor = 8_734,
            occurredOn = "2026-08-19",
            category = SampleCategories.groceries,
            receiptImagePath = "receipts/exp-11.jpg",
        ),
        expense(
            id = "exp-12",
            merchant = "MTA MetroCard",
            amountMinor = 3_300,
            occurredOn = "2026-08-18",
            category = SampleCategories.transport,
        ),
        expense(
            // Long merchant name with punctuation, plus a note long enough to wrap.
            id = "exp-13",
            merchant = "Dr. Patel — Dental Associates",
            amountMinor = 22_500,
            occurredOn = "2026-08-17",
            category = SampleCategories.health,
            note = "Routine cleaning. Insurance reimbursement still pending as of the 26th.",
            receiptImagePath = "receipts/exp-13.jpg",
            syncState = SyncState.PENDING,
            updatedAt = "2026-08-26T08:44:00Z",
        ),
        expense(
            id = "exp-14",
            merchant = "Starbucks",
            amountMinor = 585,
            occurredOn = "2026-08-17",
            category = SampleCategories.dining,
        ),
        expense(
            id = "exp-15",
            merchant = "IKEA",
            amountMinor = 45_990,
            occurredOn = "2026-08-15",
            category = SampleCategories.shopping,
            receiptImagePath = "receipts/exp-15.jpg",
        ),
        expense(
            id = "exp-16",
            merchant = "Verizon Wireless",
            amountMinor = 9_500,
            occurredOn = "2026-08-14",
            category = SampleCategories.utilities,
        ),
        expense(
            // Cash, so no receipt and nothing to sync yet.
            id = "exp-17",
            merchant = "Local Farmers Market",
            amountMinor = 2_150,
            occurredOn = "2026-08-13",
            category = SampleCategories.groceries,
            syncState = SyncState.PENDING,
            updatedAt = "2026-08-13T10:30:00Z",
        ),
        expense(
            id = "exp-18",
            merchant = "Delta Air Lines",
            amountMinor = 187_400,
            occurredOn = "2026-08-10",
            category = SampleCategories.transport,
            note = "SFO → JFK, work trip, reimbursable",
            receiptImagePath = "receipts/exp-18.jpg",
        ),
    )

    /**
     * A soft-deleted row. Kept out of [all] on purpose: list queries must filter tombstones, and
     * having one available makes it possible to write the test that proves they do.
     */
    val tombstone: Expense = expense(
        id = "exp-19",
        merchant = "Duplicate — Trader Joe's",
        amountMinor = 4_287,
        occurredOn = "2026-08-26",
        category = SampleCategories.groceries,
        updatedAt = "2026-08-26T19:10:00Z",
        isDeleted = true,
    )

    val allIncludingDeleted: List<Expense> = all + tombstone

    /** Convenience handles for single-item previews. */
    val single: Expense = all.first()
    val longMerchantName: Expense = all[1]
    val conflicted: Expense = all[5]
    val withoutReceipt: Expense = all[16]
}

object SampleBudgets {
    /** Limits chosen so the six categories land two UNDER, two NEAR, and two OVER for August 2026. */
    val all: List<Budget> = listOf(
        budget(SampleCategories.groceries, limitMinor = 30_000), // 27,821 spent -> NEAR
        budget(SampleCategories.dining, limitMinor = 15_000), //  2,755 spent -> UNDER
        budget(SampleCategories.transport, limitMinor = 100_000), // 197,750 spent -> OVER
        budget(SampleCategories.utilities, limitMinor = 25_000), // 23,820 spent -> NEAR
        budget(SampleCategories.shopping, limitMinor = 150_000), // 287,189 spent -> OVER
        budget(SampleCategories.health, limitMinor = 40_000), // 22,699 spent -> UNDER
    )

    val byCategoryId: Map<String, Budget> = all.associateBy(Budget::categoryId)
}

private const val SAMPLE_CURRENCY = "USD"

private fun expense(
    id: String,
    merchant: String,
    amountMinor: Long,
    occurredOn: String,
    category: Category,
    note: String? = null,
    receiptImagePath: String? = null,
    syncState: SyncState = SyncState.SYNCED,
    updatedAt: String = "${occurredOn}T12:00:00Z",
    isDeleted: Boolean = false,
) = Expense(
    id = id,
    merchant = merchant,
    amountMinor = amountMinor,
    currency = SAMPLE_CURRENCY,
    occurredOn = LocalDate.parse(occurredOn),
    categoryId = category.id,
    note = note,
    receiptImagePath = receiptImagePath,
    syncState = syncState,
    updatedAt = Instant.parse(updatedAt),
    isDeleted = isDeleted,
)

private fun budget(
    category: Category,
    limitMinor: Long,
) = Budget(
    categoryId = category.id,
    limitMinor = limitMinor,
    currency = SAMPLE_CURRENCY,
    month = AUGUST_2026,
)
