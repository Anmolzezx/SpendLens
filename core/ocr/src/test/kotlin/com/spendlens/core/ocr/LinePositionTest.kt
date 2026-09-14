package com.spendlens.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class LinePositionTest {
    @Test
    fun `maps the centre of the box to a fraction of image height`() {
        assertEquals(0.5f, verticalPositionOf(boxTop = 400, boxBottom = 600, imageHeight = 1_000), 0.001f)
    }

    @Test
    fun `a line at the very top is near zero`() {
        assertEquals(0.01f, verticalPositionOf(boxTop = 0, boxBottom = 20, imageHeight = 1_000), 0.001f)
    }

    @Test
    fun `a line at the very bottom is near one`() {
        assertEquals(0.99f, verticalPositionOf(boxTop = 980, boxBottom = 1_000, imageHeight = 1_000), 0.001f)
    }

    /** A box can extend past the image edge after rotation; clamping keeps the contract 0f..1f. */
    @Test
    fun `coerces a box that overruns the image`() {
        assertEquals(1f, verticalPositionOf(boxTop = 1_200, boxBottom = 1_400, imageHeight = 1_000), 0.001f)
        assertEquals(0f, verticalPositionOf(boxTop = -400, boxBottom = -200, imageHeight = 1_000), 0.001f)
    }

    /** No geometry must not mean a divide-by-zero crash mid-capture. */
    @Test
    fun `a zero-height image degrades instead of dividing by zero`() {
        assertEquals(0f, verticalPositionOf(boxTop = 10, boxBottom = 20, imageHeight = 0), 0.001f)
    }
}
