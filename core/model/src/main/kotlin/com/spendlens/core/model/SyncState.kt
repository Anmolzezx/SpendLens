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
     * Note: a pure last-write-wins resolution can never produce this state — it silently discards
     * the losing edit. This value exists because the sync design in phase 3 is expected to detect
     * divergence and surface it, rather than drop a user's data on the floor.
     */
    CONFLICT,
}
