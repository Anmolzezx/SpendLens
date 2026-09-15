package com.spendlens.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One local change to upload. */
@Serializable
data class NetworkPushRequest(
    val expense: NetworkExpense,
    /**
     * The server version this edit started from — the last one both sides agreed on. Null for a record
     * the server has never acknowledged. Always encoded, even when null: "new record" must not be
     * indistinguishable from "client forgot the field".
     */
    @SerialName("base_version")
    val baseVersion: Long?,
)

/** The server's answer to one [NetworkPushRequest]. */
@Serializable
sealed interface NetworkPushResult {
    val id: String

    /** Written. [version] is the record's new server version. */
    @Serializable
    @SerialName("accepted")
    data class Accepted(
        override val id: String,
        val version: Long,
    ) : NetworkPushResult

    /**
     * Refused: the record changed on the server after [NetworkPushRequest.baseVersion], so this edit
     * was made without seeing that one. Nothing was written. [current] is what the server holds.
     */
    @Serializable
    @SerialName("conflict")
    data class Conflict(
        val current: NetworkExpense,
    ) : NetworkPushResult {
        override val id: String get() = current.id
    }
}

/**
 * Records changed after a cursor, oldest change first.
 *
 * Each record appears at most once, at its latest version: the server sends current state, not a log
 * of every edit, so a device that was offline for a month downloads each expense once.
 */
@Serializable
data class NetworkChangePage(
    val changes: List<NetworkExpense>,
    /** Pass as `since` on the next request. The highest version on this page. */
    @SerialName("next_cursor")
    val nextCursor: Long,
    @SerialName("has_more")
    val hasMore: Boolean,
)
