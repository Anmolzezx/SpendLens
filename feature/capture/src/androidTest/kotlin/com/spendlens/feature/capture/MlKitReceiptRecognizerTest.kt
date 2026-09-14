package com.spendlens.feature.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendlens.core.ocr.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Runs the real, bundled ML Kit model against a rendered receipt.
 *
 * The parser has 18 JVM tests, but they feed it hand-typed transcripts. Nothing had ever checked the
 * seam in between: that ML Kit's actual output — its line splitting, bounding boxes and misreads —
 * reaches the parser in a shape the heuristics handle. This is that check.
 *
 * The receipt is drawn rather than photographed so the test is deterministic and needs no fixture
 * file. Clean rendered text is a best case; it proves the plumbing, not accuracy on a crumpled print.
 */
@RunWith(AndroidJUnit4::class)
class MlKitReceiptRecognizerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var image: File

    @Before
    fun setUp() {
        image = File(context.cacheDir, "rendered-receipt.jpg")
    }

    @After
    fun tearDown() {
        image.delete()
    }

    @Test
    fun readsMerchantTotalAndDateFromARenderedReceipt() =
        runTest {
            renderReceipt(
                image,
                "TRADER JOE'S",
                "1200 Market Street",
                "San Francisco CA",
                "",
                "Bananas          1.99",
                "Oat Milk         4.49",
                "Coffee          12.99",
                "",
                "SUBTOTAL        19.47",
                "TAX              1.75",
                "TOTAL           21.22",
                "",
                "08/26/2026",
            )
            val recognizer = MlKitReceiptRecognizer(
                parser = ReceiptParser(locale = Locale.US, currencyCode = "USD"),
                ioDispatcher = Dispatchers.IO,
            )

            val result = recognizer.recognize(image)

            assertEquals("TRADER JOE'S", result.merchant)
            // The subtotal trap, through the real model: 21.22, not 19.47.
            assertEquals(2_122L, result.totalMinor)
            assertEquals(LocalDate.of(2026, 8, 26), result.date)
        }

    @Test
    fun blankImageYieldsAnEmptyResultRatherThanThrowing() =
        runTest {
            renderReceipt(image)
            val recognizer = MlKitReceiptRecognizer(
                parser = ReceiptParser(locale = Locale.US, currencyCode = "USD"),
                ioDispatcher = Dispatchers.IO,
            )

            assertEquals(true, recognizer.recognize(image).isEmpty)
        }

    /** Large monospace black-on-white text: the conditions ML Kit is built for. */
    private fun renderReceipt(
        file: File,
        vararg lines: String,
    ) {
        val width = 1_080
        val lineHeight = 90
        val height = maxOf(lineHeight * (lines.size + 2), 600)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
            typeface = Typeface.MONOSPACE
        }
        lines.forEachIndexed { index, text ->
            canvas.drawText(text, 60f, lineHeight * (index + 1.5f), paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
    }
}
