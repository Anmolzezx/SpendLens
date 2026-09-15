package com.spendlens.server

import com.spendlens.core.protocol.NetworkExpense
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.SpendLensJson
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the HTTP layer adds on top of the protocol: who may call it, and what it refuses to store.
 * The protocol rules themselves are `SyncProtocolContract`, run from the app side over real HTTP.
 */
class SyncModuleTest {
    private val database = File.createTempFile("spendlens-server", ".db")
    private val store = SqliteExpenseStore(database.absolutePath)

    @After
    fun tearDown() {
        store.close()
        database.delete()
    }

    @Test
    fun `health needs no token`() =
        spendLensApp {
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
        }

    @Test
    fun `sync endpoints need the token`() =
        spendLensApp {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/expenses/changes").status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/v1/expenses/changes") { bearerAuth("wrong") }.status,
            )
            assertEquals(HttpStatusCode.OK, client.get("/v1/expenses/changes") { bearerAuth(TOKEN) }.status)
        }

    /** Stored, a bad date would break the sync of every device that pulled it, on every attempt. */
    @Test
    fun `refuses an expense whose date would not parse on a device`() =
        spendLensApp {
            val response = push(expense().copy(occurredOn = "15/09/2026"))

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("occurred_on"))
            assertEquals(0, store.changes(since = 0, limit = 10).changes.size)
        }

    @Test
    fun `refuses malformed JSON`() =
        spendLensApp {
            val response = client.post("/v1/expenses/push") {
                bearerAuth(TOKEN)
                contentType(ContentType.Application.Json)
                setBody("[{\"not\": \"an expense\"}]")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `caps the page size a client can ask for`() =
        spendLensApp {
            val tooMany = MAX_PAGE_SIZE + 1
            repeat(tooMany) { store.push(listOf(NetworkPushRequest(expense("e$it"), baseVersion = null))) }

            val body = client.get("/v1/expenses/changes?since=0&limit=100000") { bearerAuth(TOKEN) }.bodyAsText()

            val page = SpendLensJson.decodeFromString<com.spendlens.core.protocol.NetworkChangePage>(body)
            assertEquals(MAX_PAGE_SIZE, page.changes.size)
            assertTrue(page.hasMore)
        }

    private suspend fun ApplicationTestBuilder.push(expense: NetworkExpense) =
        client.post("/v1/expenses/push") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(SpendLensJson.encodeToString(listOf(NetworkPushRequest(expense, baseVersion = null))))
        }

    private fun spendLensApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { spendLensModule(store, TOKEN) }
            block()
        }

    private companion object {
        const val TOKEN = "test-token"
    }
}

internal fun expense(id: String = "a") =
    NetworkExpense(
        id = id,
        merchant = "Blue Bottle",
        amountMinor = 650,
        currency = "USD",
        occurredOn = "2026-09-15",
        categoryId = "cat-dining",
        updatedAt = "2026-09-15T08:00:00Z",
    )
