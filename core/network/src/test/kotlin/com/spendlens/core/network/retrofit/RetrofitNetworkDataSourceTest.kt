package com.spendlens.core.network.retrofit

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.ServerSocket

/**
 * Which failures come out as `IOException` — the only thing the sync worker retries.
 *
 * Getting this wrong fails quietly in both directions: a transient outage that is not retried leaves
 * changes unsynced until the next trigger, and a wrong token that *is* retried burns battery for
 * minutes on a request that can never succeed.
 */
class RetrofitNetworkDataSourceTest {
    private val servers = mutableListOf<TestHttpServer>()

    @After
    fun stopServers() = servers.forEach { it.close() }

    @Test
    fun `a wrong token is not retryable`() =
        runTest {
            val server = TestHttpServer.spendLens().also { servers += it }

            val failure = runCatching { server.dataSource(token = "wrong").pullExpenses(since = 0, limit = 10) }

            val http = failure.exceptionOrNull() as? HttpException
            assertEquals(401, http?.code())
        }

    @Test
    fun `a server error is retryable`() =
        runTest {
            assertTrue(failureFromServerAnswering(HttpStatusCode.ServiceUnavailable) is IOException)
        }

    @Test
    fun `rate limiting is retryable`() =
        runTest {
            assertTrue(failureFromServerAnswering(HttpStatusCode.TooManyRequests) is IOException)
        }

    @Test
    fun `a request the server refuses is not retryable`() =
        runTest {
            assertTrue(failureFromServerAnswering(HttpStatusCode.BadRequest) is HttpException)
        }

    @Test
    fun `no server at all is retryable`() =
        runTest {
            // A port that was free a moment ago, so nothing is listening on it.
            val port = ServerSocket(0).use { it.localPort }
            val dataSource = RetrofitNetworkDataSource("http://127.0.0.1:$port/", "token", OkHttpClient())

            assertTrue(runCatching { dataSource.pullExpenses(since = 0, limit = 10) }.exceptionOrNull() is IOException)
        }

    private suspend fun failureFromServerAnswering(status: HttpStatusCode): Throwable? {
        val server = TestHttpServer { routing { get("/v1/expenses/changes") { call.respond(status) } } }
        servers += server
        return runCatching { server.dataSource().pullExpenses(since = 0, limit = 10) }.exceptionOrNull()
    }
}
