package com.spendlens.core.model

/**
 * Where a record stands relative to the remote copy.
 *
 * [PENDING] is the default for anything created or edited on this device: every write lands in the
 * local database first and is uploaded later, so the app never blocks on the network.
 */
enum class SyncState {
    /** Written locally, not yet acknowledged by the server. */
    PENDING,

    /** Local and remote agree as of the last sync. */
    SYNCED,

    /**
     * The same record was edited on two devices and the two edits diverged.
     *
     * Kept deliberately (decided 2026-09-15) instead of resolving by last-write-wins. LWW can never
     * produce this state — it silently throws away the older edit, and the user never learns an edit
     * was lost. Sync instead detects divergence through [Expense.remoteVersion], keeps both versions,
     * and asks the user which to keep.
     */
    CONFLICT,
}
