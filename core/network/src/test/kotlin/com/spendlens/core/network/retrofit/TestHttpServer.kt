package com.spendlens.core.network.retrofit

import com.spendlens.server.SqliteExpenseStore
import com.spendlens.server.spendLensModule
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File

/**
 * A real HTTP server on a free local port, for Retrofit to talk to over an actual socket.
 *
 * Deliberately not Ktor's in-memory `testApplication`: that skips the network, and the point here is
 * to exercise OkHttp, Retrofit's converters and Netty together, exactly as a device would.
 */
internal class TestHttpServer(
    module: Application.() -> Unit,
) : AutoCloseable {
    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1", module = module).start(wait = false)

    private val port: Int = runBlocking {
        server.engine
            .resolvedConnectors()
            .first()
            .port
    }

    val baseUrl: String = "http://127.0.0.1:$port/"

    fun dataSource(token: String = TOKEN) = RetrofitNetworkDataSource(baseUrl, token, OkHttpClient())

    override fun close() = server.stop(gracePeriodMillis = 0, timeoutMillis = 0)

    companion object {
        const val TOKEN = "test-token"

        /** The production server module, on a fresh database file. */
        fun spendLens(): TestHttpServer {
            val database = File.createTempFile("spendlens-server", ".db").apply { deleteOnExit() }
            val store = SqliteExpenseStore(database.absolutePath)
            return TestHttpServer { spendLensModule(store, TOKEN) }
        }
    }
}
