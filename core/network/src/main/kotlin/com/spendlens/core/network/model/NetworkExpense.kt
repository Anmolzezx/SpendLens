package com.spendlens.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An expense as it travels over the wire.
 *
 * Its own type rather than the domain `Expense`, for the same reason `ExpenseEntity` is: the wire
 * format is a contract with a server, and it must not change because a screen needed a field.
 *
 * Deliberately absent:
 * - **`receipt_image_path`** — a path into this device's private storage, meaningless on any other.
 *   Receipt photos stay on the device that took them.
 * - **`sync_state`** — a device's opinion of itself, not a property of the record.
 *
 * Dates are ISO-8601 strings. JSON has no date type, and a string is unambiguous in a way an epoch
 * number is not: "2026-09-15" cannot be misread as milliseconds, seconds, or days.
 */
@Serializable
data class NetworkExpense(
    val id: String,
    val merchant: String,
    @SerialName("amount_minor")
    val amountMinor: Long,
    val currency: String,
    /** A calendar date, "2026-09-15". */
    @SerialName("occurred_on")
    val occurredOn: String,
    @SerialName("category_id")
    val categoryId: String,
    val note: String? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    /** When the edit was made, by the editing device's clock. Informational only — see [version]. */
    @SerialName("updated_at")
    val updatedAt: String,
    /**
     * Assigned by the server on every accepted write, from one counter shared by all records, so it
     * orders changes and doubles as the pull cursor. Null in a push: the client never invents one.
     *
     * Devices' clocks disagree, so ordering edits by [updatedAt] would let a phone with a fast clock
     * win every argument. A server-assigned number cannot drift.
     */
    val version: Long? = null,
)
