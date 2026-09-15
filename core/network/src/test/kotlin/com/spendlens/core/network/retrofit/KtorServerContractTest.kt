package com.spendlens.core.network.retrofit

import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.testing.network.SyncProtocolContract
import org.junit.After

/**
 * The protocol rules, against production code end to end: this app's Retrofit client, over a real
 * socket, to the Ktor server in `:server`, storing in SQLite. The same suite runs against the fake.
 */
class KtorServerContractTest : SyncProtocolContract() {
    private val servers = mutableListOf<TestHttpServer>()

    override fun newServer(): SpendLensNetworkDataSource =
        TestHttpServer.spendLens().also { servers += it }.dataSource()

    @After
    fun stopServers() = servers.forEach { it.close() }
}
