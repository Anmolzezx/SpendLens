package com.spendlens.core.ocr

/**
 * One line of recognised text with where it sat on the page.
 *
 * This module deliberately does **not** depend on ML Kit. The parser is the risky, heuristic part of
 * receipt capture, and keeping it behind a plain data type means it can be developed and tested on
 * the JVM against real receipt transcripts — no camera, no emulator, no device. `feature:capture`
 * adapts `Text.Line` into this and nothing else changes.
 */
data class RecognizedLine(
    val text: String,
    /**
     * Vertical position of the line's centre, 0f at the top of the image and 1f at the bottom.
     *
     * Position matters: a merchant name is at the top of a receipt and a total is near the bottom,
     * and that ordering survives when the text itself is garbled.
     */
    val verticalPosition: Float,
    /**
     * Left edge as a fraction of image width. Orders the pieces of a row that ML Kit split apart —
     * a receipt prints the label on the left and its amount far to the right.
     */
    val horizontalPosition: Float = 0f,
    /**
     * Line height as a fraction of image height. Zero means the source has no geometry, and such
     * lines are never merged into rows.
     */
    val heightFraction: Float = 0f,
)

/** The full recognition result, ordered top to bottom. */
data class RecognizedText(
    val lines: List<RecognizedLine>,
) {
    companion object {
        /**
         * Builds a result from plain text, spacing lines evenly down the page.
         *
         * Used by tests and by any source that has no geometry — the parser degrades to
         * text-only heuristics rather than failing.
         */
        fun fromPlainText(raw: String): RecognizedText {
            val lines = raw.lines().map(String::trim).filter(String::isNotEmpty)
            if (lines.isEmpty()) return RecognizedText(emptyList())
            return RecognizedText(
                lines.mapIndexed { index, text ->
                    RecognizedLine(text = text, verticalPosition = index.toFloat() / lines.size)
                },
            )
        }
    }
}
