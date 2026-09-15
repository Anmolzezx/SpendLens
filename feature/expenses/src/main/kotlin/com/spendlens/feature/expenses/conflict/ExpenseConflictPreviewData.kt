package com.spendlens.feature.expenses.conflict

import com.spendlens.core.model.ExpenseConflict
import com.spendlens.core.model.SyncState
import com.spendlens.core.model.sample.SampleCategories
import com.spendlens.core.model.sample.SampleExpenses
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** Locale and zone pinned, so previews render the same edit times on any machine. */
internal object ExpenseConflictPreviewData {
    private val base = SampleExpenses.withoutReceipt.copy(syncState = SyncState.CONFLICT)

    /** The phone changed the amount; the tablet changed the amount and added a note. */
    val editedOnBoth: ExpenseConflictUiState = deciding(
        ExpenseConflict(
            local = base.copy(amountMinor = 440_000, updatedAt = Instant.parse("2026-09-15T12:58:00Z")),
            remote = base.copy(
                amountMinor = 500_000,
                note = "Corrected on the tablet",
                updatedAt = Instant.parse("2026-09-15T13:00:00Z"),
            ),
        ),
    )

    /** Deleted on this device while the other device was still editing it. */
    val deletedHere: ExpenseConflictUiState = deciding(
        ExpenseConflict(
            local = base.copy(isDeleted = true, updatedAt = Instant.parse("2026-09-15T12:58:00Z")),
            remote = base.copy(merchant = "${base.merchant} (still needed)"),
        ),
    )

    private fun deciding(conflict: ExpenseConflict) =
        ExpenseConflictUiState.Deciding(
            conflict = conflict.toUiModel(SampleCategories.byId, ZoneOffset.UTC, Locale.US),
            isSaving = false,
        )
}
