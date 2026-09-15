package com.spendlens.core.data.sync

/**
 * Runs one complete sync pass.
 *
 * An interface so the background worker can be tested against a fake that fails on demand, without a
 * database or a server behind it.
 */
interface Synchronizer {
    suspend fun sync(): SyncReport
}

/** What one sync pass did. */
data class SyncReport(
    /** Local changes the server accepted. */
    val pushed: Int = 0,
    /** Server changes written to this device. */
    val pulled: Int = 0,
    /** Expenses found changed on both sides, or whose other side changed again. */
    val conflicts: Int = 0,
) {
    operator fun plus(other: SyncReport) =
        SyncReport(pushed + other.pushed, pulled + other.pulled, conflicts + other.conflicts)
}
