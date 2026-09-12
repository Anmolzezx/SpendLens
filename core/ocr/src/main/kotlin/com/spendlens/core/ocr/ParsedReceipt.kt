package com.spendlens.core.ocr

import java.time.LocalDate

/**
 * What the parser managed to extract. Every field is nullable, and that is the design.
 *
 * OCR on a crumpled receipt is unreliable, and a parser that guesses confidently is worse than one
 * that admits it does not know — a wrong amount silently saved is a corrupted ledger, whereas a
 * blank field is a prompt. The review screen shows whatever was found and lets the user correct it.
 */
data class ParsedReceipt(
    val merchant: String?,
    val totalMinor: Long?,
    val date: LocalDate?,
) {
    /** True when nothing usable came back, so the UI can say so rather than showing an empty form. */
    val isEmpty: Boolean get() = merchant == null && totalMinor == null && date == null

    companion object {
        val EMPTY = ParsedReceipt(merchant = null, totalMinor = null, date = null)
    }
}
