package com.spendlens.core.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the JSON a server will see and send. A renamed Kotlin property would otherwise change the wire
 * format silently, and the first sign would be a production server rejecting every request.
 */
class SyncProtocolJsonTest {
    @Test
    fun `decodes push results by their type field`() {
        val json =
            """
            [
              {"type": "accepted", "id": "a", "version": 42},
              {"type": "conflict", "current": {
                "id": "b", "merchant": "Blue Bottle", "amount_minor": 650, "currency": "USD",
                "occurred_on": "2026-09-15", "category_id": "cat-dining", "note": null,
                "is_deleted": false, "updated_at": "2026-09-15T08:00:00Z", "version": 43
              }}
            ]
            """.trimIndent()

        val results = SpendLensJson.decodeFromString<List<NetworkPushResult>>(json)

        assertEquals(NetworkPushResult.Accepted(id = "a", version = 42), results[0])
        val conflict = results[1] as NetworkPushResult.Conflict
        assertEquals("b", conflict.id)
        assertEquals(43L, conflict.current.version)
    }

    @Test
    fun `ignores fields this version of the app does not know`() {
        val json =
            """
            {"id": "a", "merchant": "Shop", "amount_minor": 100, "currency": "USD",
             "occurred_on": "2026-09-15", "category_id": "cat-other",
             "updated_at": "2026-09-15T08:00:00Z", "version": 1, "shared_with": ["someone"]}
            """.trimIndent()

        assertEquals("Shop", SpendLensJson.decodeFromString<NetworkExpense>(json).merchant)
    }

    @Test
    fun `a new record is pushed with an explicit null base version`() {
        val request = NetworkPushRequest(expense = expense(), baseVersion = null)

        val encoded = SpendLensJson.encodeToJsonElement(NetworkPushRequest.serializer(), request).jsonObject

        assertEquals(JsonNull, encoded["base_version"])
    }

    @Test
    fun `defaults are sent rather than left for the server to assume`() {
        val encoded = SpendLensJson.encodeToJsonElement(NetworkExpense.serializer(), expense()).jsonObject

        assertTrue("is_deleted" in encoded)
        assertTrue("note" in encoded)
    }

    private fun expense() =
        NetworkExpense(
            id = "a",
            merchant = "Shop",
            amountMinor = 100,
            currency = "USD",
            occurredOn = "2026-09-15",
            categoryId = "cat-other",
            updatedAt = "2026-09-15T08:00:00Z",
        )
}
