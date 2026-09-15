package com.spendlens.core.protocol

import kotlinx.serialization.json.Json

/**
 * The one wire-format configuration, used by the app's HTTP client, the Ktor server and the tests alike,
 * so all three read and write exactly the same JSON.
 *
 * - `ignoreUnknownKeys`: the server can add a field without breaking every installed copy of the app.
 * - `encodeDefaults`: every field is always sent. A server should not have to know the client's
 *   defaults to read a request.
 */
val SpendLensJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
