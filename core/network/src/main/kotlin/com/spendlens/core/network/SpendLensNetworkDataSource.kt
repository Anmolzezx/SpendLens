package com.spendlens.core.network

import com.spendlens.core.network.model.NetworkChangePage
import com.spendlens.core.network.model.NetworkPushRequest
import com.spendlens.core.network.model.NetworkPushResult

/**
 * The sync protocol. Any backend — Retrofit to a real server, or the in-memory fake the tests share
 * between two simulated devices — implements exactly this.
 *
 * **Versions.** Every accepted write gets the next number from a single server-wide counter. A
 * device remembers, per record, the version it last agreed with the server on.
 *
 * **Push** is a compare-and-set. A write is accepted only if the record's current version is still the
 * one the device based its edit on (or the record is new). Otherwise someone else changed it first,
 * and the server refuses and returns its copy instead of overwriting either edit. That refusal is how
 * a conflict is *detected* rather than silently resolved by whichever device synced last.
 *
 * **Pull** returns records whose version is greater than a cursor. Because versions come from one
 * counter, "everything after 41" is a complete, gap-free answer.
 *
 * Failures are thrown as `IOException`. Whether and when to retry is the caller's decision, not the
 * transport's.
 */
interface SpendLensNetworkDataSource {
    /** One result per request, in any order; match them by id. */
    suspend fun pushExpenses(requests: List<NetworkPushRequest>): List<NetworkPushResult>

    /** A server may return fewer than [limit] and set `hasMore`; callers must follow it. */
    suspend fun pullExpenses(
        since: Long,
        limit: Int,
    ): NetworkChangePage
}
