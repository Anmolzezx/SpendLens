package com.spendlens.core.network.retrofit

import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.protocol.NetworkChangePage
import com.spendlens.core.protocol.NetworkPushRequest
import com.spendlens.core.protocol.NetworkPushResult
import com.spendlens.core.protocol.SpendLensJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException

private interface SpendLensApi {
    @POST("v1/expenses/push")
    suspend fun push(
        @Body requests: List<NetworkPushRequest>,
    ): List<NetworkPushResult>

    @GET("v1/expenses/changes")
    suspend fun changes(
        @Query("since") since: Long,
        @Query("limit") limit: Int,
    ): NetworkChangePage
}

/**
 * The sync protocol over HTTP, against the Ktor server in `:server`.
 *
 * **Which failures are worth retrying** is decided here, because only this layer knows about HTTP.
 * The sync worker retries `IOException` and nothing else, so:
 *
 * - No connection, a timeout, a dropped response — already `IOException`s.
 * - `5xx`, `429 Too Many Requests`, `408 Request Timeout` — the server may well answer next time, so
 *   they become `IOException`s too.
 * - Any other `4xx` — a wrong token, a request the server refuses — would be refused identically on
 *   every retry. Left as `HttpException`, so the work fails at once instead of retrying for minutes.
 */
class RetrofitNetworkDataSource(
    baseUrl: String,
    token: String,
    client: OkHttpClient,
) : SpendLensNetworkDataSource {
    private val api = Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client.newBuilder().addInterceptor(BearerTokenInterceptor(token)).build())
        .addConverterFactory(SpendLensJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SpendLensApi::class.java)

    override suspend fun pushExpenses(requests: List<NetworkPushRequest>): List<NetworkPushResult> =
        retryableOnServerError { api.push(requests) }

    override suspend fun pullExpenses(
        since: Long,
        limit: Int,
    ): NetworkChangePage = retryableOnServerError { api.changes(since, limit) }

    private inline fun <T> retryableOnServerError(call: () -> T): T =
        try {
            call()
        } catch (e: HttpException) {
            throw if (e.isTransient()) IOException("Server returned ${e.code()}", e) else e
        }

    private fun HttpException.isTransient() =
        code() >= HTTP_SERVER_ERROR || code() == HTTP_TOO_MANY_REQUESTS || code() == HTTP_REQUEST_TIMEOUT

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}

/**
 * Adds the device's bearer token to every request.
 *
 * A static token, so there is nothing to refresh and no OkHttp `Authenticator`. Signing in with
 * expiring tokens would add one; a single-user server does not need it.
 */
private class BearerTokenInterceptor(
    private val token: String,
) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
}
