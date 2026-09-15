package com.spendlens.core.testing.network

/** The fake the sync engine's tests rely on, held to the same rules as the real server. */
class FakeSpendLensServerContractTest : SyncProtocolContract() {
    override fun newServer() = FakeSpendLensServer()
}
