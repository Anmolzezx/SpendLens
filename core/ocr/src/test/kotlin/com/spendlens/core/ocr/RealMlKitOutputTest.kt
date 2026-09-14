package com.spendlens.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The parser against **actual ML Kit output**, captured on an Android 17 emulator.
 *
 * Every string and bounding box below is verbatim from the bundled text-recognition model, run on a
 * rendered receipt saved as a JPEG and decoded again — the same path a camera capture takes. Two
 * things about it broke the parser, and neither appears in a hand-typed transcript:
 *
 *  - Labels and amounts come back as **separate lines** on the same row.
 *  - The date came back as **"08/26/ 2026"**, with a space the receipt never printed.
 *
 * Keeping the real output as a JVM fixture means both regressions are caught in milliseconds on
 * every push, without an emulator.
 */
class RealMlKitOutputTest {
    private val parser = ReceiptParser(locale = Locale.US, currencyCode = "USD")

    @Test
    fun `parses a receipt exactly as the real model returned it`() {
        val result = parser.parse(RecognizedText(CAPTURED.map(::toLine)))

        assertEquals("TRADER JOE'S", result.merchant)
        assertEquals(2_122L, result.totalMinor)
        assertEquals(LocalDate.of(2026, 8, 26), result.date)
    }

    @Test
    fun `reads a date with the stray space ML Kit inserts after a separator`() {
        assertEquals(LocalDate.of(2026, 8, 26), "08/26/ 2026".findDate(Locale.US))
        assertEquals(LocalDate.of(2026, 8, 26), "08 /26/2026".findDate(Locale.US))
    }

    /** Tolerance is one space, not any whitespace, or unrelated numbers fuse into a date. */
    @Test
    fun `two numbers separated by wide spacing are not a date`() {
        assertEquals(null, "12   /   5   /   2026".findDate(Locale.US))
    }

    private data class Box(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    /** Same arithmetic as `MlKitReceiptRecognizer`'s adapter, so the fixture exercises the real mapping. */
    private fun toLine(box: Box) =
        RecognizedLine(
            text = box.text,
            verticalPosition = verticalPositionOf(box.top, box.bottom, IMAGE_HEIGHT),
            horizontalPosition = box.left.toFloat() / IMAGE_WIDTH,
            heightFraction = (box.bottom - box.top).toFloat() / IMAGE_HEIGHT,
        )

    private companion object {
        const val IMAGE_WIDTH = 1_080
        const val IMAGE_HEIGHT = 1_350

        /** ML Kit's reporting order, which is not top-to-bottom: amounts arrive last. */
        val CAPTURED = listOf(
            Box("TRADER JOE'S", 62, 94, 464, 136),
            Box("1200 Market Street", 66, 182, 666, 226),
            Box("San Francisco CA", 82, 270, 602, 322),
            Box("Bananas", 63, 453, 291, 498),
            Box("Oat Milk", 62, 539, 327, 588),
            Box("Coffee", 63, 629, 259, 678),
            Box("SUBTOTAL", 63, 814, 327, 856),
            Box("TAX", 63, 905, 161, 945),
            Box("TOTAL", 62, 992, 224, 1_035),
            Box("08/26/ 2026", 64, 1_174, 396, 1_216),
            Box("1.99", 644, 454, 770, 496),
            Box("4.49", 640, 544, 770, 586),
            Box("12.99", 609, 631, 769, 676),
            Box("19.47", 611, 814, 770, 856),
            Box("1.75", 644, 904, 768, 946),
            Box("21.22", 607, 991, 769, 1_037),
        )
    }
}
