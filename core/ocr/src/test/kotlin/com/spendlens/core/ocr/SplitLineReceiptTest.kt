package com.spendlens.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Receipts as ML Kit actually returns them, not as a person would type them.
 *
 * On a printed receipt the label sits on the left and the amount on the right, far apart. ML Kit
 * reports those as **separate lines** at the same height. The first on-device run of the real model
 * showed the parser had only ever been tested against the typed shape — "TOTAL 21.22" on one line —
 * and returned the receipt's year, 2026, as the total. Everything here reproduces that geometry.
 */
class SplitLineReceiptTest {
    private val parser = ReceiptParser(locale = Locale.US, currencyCode = "USD")

    /** Builds a line with real-looking geometry: centre, left edge and height as image fractions. */
    private fun line(
        text: String,
        top: Float,
        left: Float = 0.05f,
        height: Float = LINE_HEIGHT,
    ) = RecognizedLine(
        text = text,
        verticalPosition = top + height / 2,
        horizontalPosition = left,
        heightFraction = height,
    )

    @Test
    fun `joins a label and an amount that ML Kit split onto the same row`() {
        val result = parser.parse(
            RecognizedText(
                listOf(
                    line("CORNER STORE", top = 0.02f),
                    line("SUBTOTAL", top = 0.60f),
                    line("19.47", top = 0.60f, left = 0.75f),
                    line("TOTAL", top = 0.70f),
                    line("21.22", top = 0.70f, left = 0.75f),
                ),
            ),
        )

        assertEquals(2_122L, result.totalMinor)
    }

    /** The exact failure from the emulator: the year was the largest number on the receipt. */
    @Test
    fun `a year in a date is never read as an amount`() {
        val result = parser.parse(
            RecognizedText(
                listOf(
                    line("CAFE", top = 0.02f),
                    line("Coffee", top = 0.40f),
                    line("4.50", top = 0.40f, left = 0.75f),
                    line("08/26/2026", top = 0.80f),
                ),
            ),
        )

        assertEquals(450L, result.totalMinor)
        assertEquals(LocalDate.of(2026, 8, 26), result.date)
    }

    /** A street number is an integer too; the fallback must not mistake "1200 Market St" for $1,200. */
    @Test
    fun `the unlabelled fallback prefers amounts with a decimal part`() {
        val result = parser.parse(
            RecognizedText(
                listOf(
                    line("MARKET", top = 0.02f),
                    line("1200 Market Street", top = 0.06f),
                    line("Apples", top = 0.40f),
                    line("3.75", top = 0.40f, left = 0.75f),
                ),
            ),
        )

        assertEquals(375L, result.totalMinor)
    }

    /** Adjacent rows must stay separate, or a dense receipt collapses into one line. */
    @Test
    fun `lines on neighbouring rows are not merged`() {
        val result = parser.parse(
            RecognizedText(
                listOf(
                    line("SHOP", top = 0.02f),
                    line("SUBTOTAL", top = 0.60f),
                    line("19.47", top = 0.60f, left = 0.75f),
                    // One full line height lower: a different row.
                    line("TOTAL", top = 0.60f + LINE_HEIGHT),
                    line("21.22", top = 0.60f + LINE_HEIGHT, left = 0.75f),
                ),
            ),
        )

        assertEquals(2_122L, result.totalMinor)
    }

    /** Joined left-to-right, whatever order ML Kit happened to report the pieces in. */
    @Test
    fun `row assembly orders pieces by horizontal position`() {
        val rows = assembleRows(
            listOf(
                line("21.22", top = 0.70f, left = 0.75f),
                line("TOTAL", top = 0.70f, left = 0.05f),
            ),
        )

        assertEquals(listOf("TOTAL 21.22"), rows.map { it.text })
    }

    /** Text with no geometry (transcripts, tests) must pass through untouched. */
    @Test
    fun `lines without height are never merged`() {
        val rows = assembleRows(RecognizedText.fromPlainText("A\nB\nC").lines)

        assertEquals(listOf("A", "B", "C"), rows.map { it.text })
    }

    private companion object {
        const val LINE_HEIGHT = 0.03f
    }
}
