package com.spendlens.core.network

import kotlinx.serialization.json.Json

/**
 * The one wire-format configuration, shared by the HTTP client and the contract tests so the tests
 * exercise what production sends.
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
