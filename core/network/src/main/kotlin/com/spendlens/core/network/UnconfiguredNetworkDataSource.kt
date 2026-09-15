package com.spendlens.core.network

import com.spendlens.core.network.model.NetworkChangePage
import com.spendlens.core.network.model.NetworkPushRequest
import com.spendlens.core.network.model.NetworkPushResult
import javax.inject.Inject

/**
 * Stands in until a backend is chosen, so the background sync can be wired and run end to end today.
 *
 * Throws rather than pretending to succeed: a stub that accepted every upload would mark changes synced
 * that never left the device, and nobody would notice until they were needed. Not an `IOException`
 * either — that means "try again later", and retrying cannot make a missing server appear, so the sync
 * worker fails at once.
 */
internal class UnconfiguredNetworkDataSource
    @Inject
    constructor() : SpendLensNetworkDataSource {
        override suspend fun pushExpenses(requests: List<NetworkPushRequest>): List<NetworkPushResult> = notConfigured()

        override suspend fun pullExpenses(
            since: Long,
            limit: Int,
        ): NetworkChangePage = notConfigured()

        private fun notConfigured(): Nothing = error("No sync backend is configured yet")
    }
