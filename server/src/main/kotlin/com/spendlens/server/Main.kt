package com.spendlens.server

import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = ServerConfig.fromEnvironment()
    val store = SqliteExpenseStore(config.databasePath)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        spendLensModule(store, config.token)
        monitor.subscribe(ApplicationStopped) { store.close() }
    }.start(wait = true)
}
