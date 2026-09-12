package com.spendlens.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Parser tests against transcripts shaped like real receipts.
 *
 * These run on the JVM in milliseconds with no camera and no ML Kit, which is the entire reason
 * `core:ocr` takes a plain [RecognizedText] instead of an ML Kit `Text`. The heuristics here are
 * guesses, and guesses need a regression suite more than certainties do.
 */
class ReceiptParserTest {
    private val parser = ReceiptParser(locale = Locale.US, currencyCode = "USD")

    private fun parse(raw: String) = parser.parse(RecognizedText.fromPlainText(raw))

    @Test
    fun `extracts merchant, total and date from a typical receipt`() {
        val result = parse(
            """
            TRADER JOE'S
            1200 Market Street
            San Francisco, CA 94102
            Tel: (415) 555-0142

            Bananas            1.99
            Oat Milk           4.49
            Coffee            12.99

            SUBTOTAL          19.47
            TAX                1.75
            TOTAL             21.22

            VISA ****1234
            08/26/2026
            """.trimIndent(),
        )

        assertEquals("TRADER JOE'S", result.merchant)
        assertEquals(2_122L, result.totalMinor)
        assertEquals(LocalDate.of(2026, 8, 26), result.date)
    }

    /**
     * The single most common way this heuristic breaks: "SUBTOTAL" contains "TOTAL", so a naive
     * `contains("TOTAL")` returns the pre-tax figure and every saved expense is quietly too low.
     */
    @Test
    fun `does not mistake subtotal for total`() {
        val result = parse(
            """
            CORNER STORE
            SUBTOTAL          19.47
            TAX                1.75
            TOTAL             21.22
            """.trimIndent(),
        )

        assertEquals(2_122L, result.totalMinor)
    }

    @Test
    fun `handles sub-total spelled with a space or hyphen`() {
        listOf("SUB TOTAL", "SUB-TOTAL").forEach { label ->
            val result = parse("SHOP\n$label 19.47\nTOTAL 21.22")

            assertEquals("failed for '$label'", 2_122L, result.totalMinor)
        }
    }

    /** Receipts print subtotal, tax, then the amount charged — the last labelled total wins. */
    @Test
    fun `prefers the final labelled total`() {
        val result = parse(
            """
            SHOP
            TOTAL             21.22
            AMOUNT DUE        25.00
            """.trimIndent(),
        )

        assertEquals(2_500L, result.totalMinor)
    }

    @Test
    fun `falls back to the largest amount when nothing is labelled`() {
        val result = parse(
            """
            MARKET
            Apples   2.50
            Bread    3.75
            Cheese  11.20
            """.trimIndent(),
        )

        assertEquals(1_120L, result.totalMinor)
    }

    @Test
    fun `reads amounts with grouping separators`() {
        val result = parse("ELECTRONICS STORE\nTOTAL  1,234.56")

        assertEquals(123_456L, result.totalMinor)
    }

    /** "1,234" is one thousand two hundred and thirty-four, not one point two three four. */
    @Test
    fun `treats a three-digit group as thousands, not decimals`() {
        assertEquals(123_400L, "1,234".toMinorUnitsOrNull("USD"))
        assertEquals(1_234L, "12.34".toMinorUnitsOrNull("USD"))
    }

    @Test
    fun `reads a currency symbol before the amount`() {
        val result = parse("CAFE\nTOTAL  $8.50")

        assertEquals(850L, result.totalMinor)
    }

    @Test
    fun `skips the address when finding the merchant`() {
        val result = parse(
            """
            WHOLE FOODS MARKET
            450 Rand Road
            Chicago, IL
            TOTAL 12.00
            """.trimIndent(),
        )

        assertEquals("WHOLE FOODS MARKET", result.merchant)
    }

    @Test
    fun `skips a leading receipt number when finding the merchant`() {
        val result = parse(
            """
            ============
            #0042-1189
            BLUE BOTTLE COFFEE
            TOTAL 6.75
            """.trimIndent(),
        )

        assertEquals("BLUE BOTTLE COFFEE", result.merchant)
    }

    @Test
    fun `reads an ISO date`() {
        assertEquals(LocalDate.of(2026, 8, 26), parse("SHOP\n2026-08-26\nTOTAL 1.00").date)
    }

    @Test
    fun `reads a textual date`() {
        assertEquals(LocalDate.of(2026, 8, 26), parse("SHOP\n26 Aug 2026\nTOTAL 1.00").date)
    }

    /** A day above 12 can only be day-first, whatever the locale says. */
    @Test
    fun `resolves an unambiguous day-first date regardless of locale`() {
        val result = ReceiptParser(locale = Locale.US, currencyCode = "USD")
            .parse(RecognizedText.fromPlainText("SHOP\n26/08/2026\nTOTAL 1.00"))

        assertEquals(LocalDate.of(2026, 8, 26), result.date)
    }

    /** 08/07 is genuinely ambiguous, so the locale decides — and the two must disagree. */
    @Test
    fun `uses the locale to resolve an ambiguous numeric date`() {
        val text = RecognizedText.fromPlainText("SHOP\n08/07/2026\nTOTAL 1.00")

        val us = ReceiptParser(locale = Locale.US, currencyCode = "USD").parse(text)
        val uk = ReceiptParser(locale = Locale.UK, currencyCode = "USD").parse(text)

        assertEquals(LocalDate.of(2026, 8, 7), us.date)
        assertEquals(LocalDate.of(2026, 7, 8), uk.date)
    }

    @Test
    fun `rejects an impossible date rather than guessing`() {
        assertNull(parse("SHOP\n99/99/2026\nTOTAL 1.00").date)
    }

    @Test
    fun `returns nulls rather than guessing when the text is unusable`() {
        val result = parse("~~~~~~\n######\n======")

        assertNull(result.merchant)
        assertNull(result.totalMinor)
        assertNull(result.date)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `empty input produces an empty result`() {
        assertTrue(parser.parse(RecognizedText(emptyList())).isEmpty)
        assertTrue(parse("   \n  \n ").isEmpty)
    }

    /** JPY has no minor units, so 1234 yen is 1234 — not 123400. */
    @Test
    fun `respects currency precision`() {
        val yen = ReceiptParser(locale = Locale.JAPAN, currencyCode = "JPY")
            .parse(RecognizedText.fromPlainText("SHOP\nTOTAL 1234"))

        assertEquals(1_234L, yen.totalMinor)
    }
}
