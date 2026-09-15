package com.spendlens.server

import com.spendlens.core.protocol.NetworkExpense
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import com.spendlens.core.protocol.SpendLensJson
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** The largest page a client may ask for. Bounds the memory and time a single request can take. */
internal const val MAX_PAGE_SIZE = 500

/** The largest upload batch accepted. The app sends 50 at a time. */
internal const val MAX_PUSH_BATCH = 500

private const val DEFAULT_PAGE_SIZE = 100
private const val AUTH = "device"

/**
 * The HTTP face of the sync protocol, described in `SpendLensNetworkDataSource` on the app side:
 *
 * - `POST /v1/expenses/push` — a list of writes in, one result per write out.
 * - `GET /v1/expenses/changes?since=N&limit=M` — records changed after version N.
 * - `GET /health` — unauthenticated, for a host's liveness check.
 *
 * One bearer token for the whole server. SpendLens syncs one person's devices, so there is one account;
 * a server for many users would need per-user records and a real sign-in, and this does not pretend
 * to be that.
 */
fun Application.spendLensModule(
    store: ExpenseStore,
    token: String,
) {
    install(ContentNegotiation) { json(SpendLensJson) }
    install(Authentication) {
        bearer(AUTH) {
            authenticate { credential ->
                if (constantTimeEquals(credential.token, token)) UserIdPrincipal("owner") else null
            }
        }
    }
    install(StatusPages) {
        // require(...) failures below are the client's mistake, not the server's.
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad request")
        }
    }

    routing {
        get("/health") { call.respondText("ok") }

        authenticate(AUTH) {
            route("/v1/expenses") {
                post("/push") {
                    val requests = call.receive<List<NetworkPushRequest>>()
                    require(requests.size <= MAX_PUSH_BATCH) { "At most $MAX_PUSH_BATCH writes per request" }
                    requests.forEach { it.expense.validate() }
                    // Typed as the sealed interface so each element is written with its "type" field.
                    val results: List<NetworkPushResult> = store.push(requests)
                    call.respond(results)
                }
                get("/changes") {
                    val since = call.parameters["since"]?.toLongOrNull() ?: 0L
                    require(since >= 0) { "since must not be negative" }
                    val limit = (call.parameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE).coerceIn(
                        1,
                        MAX_PAGE_SIZE,
                    )
                    call.respond(store.changes(since, limit))
                }
            }
        }
    }
}

/**
 * Rejects records that would break every device that pulls them. One unparseable date stored here
 * would make every client's sync fail on it, forever — so it is refused at the door instead.
 */
private fun NetworkExpense.validate() {
    require(id.isNotBlank()) { "Expense id must not be blank" }
    require(runCatching { LocalDate.parse(occurredOn) }.isSuccess) { "occurred_on must be an ISO date: $occurredOn" }
    require(runCatching { Instant.parse(updatedAt) }.isSuccess) { "updated_at must be an ISO instant: $updatedAt" }
}

/** Compares in time independent of where the strings differ, so response timing cannot leak the token. */
private fun constantTimeEquals(
    a: String,
    b: String,
): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
