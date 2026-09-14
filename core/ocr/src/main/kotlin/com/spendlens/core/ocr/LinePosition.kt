package com.spendlens.core.ocr

/**
 * Converts a recognised line's bounding box into the 0f..1f vertical position [RecognizedLine] uses.
 *
 * Lives here rather than in the ML Kit adapter so it can be tested: the adapter itself is then thin
 * enough to read and verify by eye, which matters because ML Kit's `Text` is effectively impossible
 * to construct in a unit test.
 *
 * @param boxTop top edge of the line's bounding box, in image pixels.
 * @param boxBottom bottom edge, in image pixels.
 * @param imageHeight height of the source image in pixels.
 */
fun verticalPositionOf(
    boxTop: Int,
    boxBottom: Int,
    imageHeight: Int,
): Float {
    // A zero-height image means the source gave us no geometry. Treat every line as top-of-page
    // rather than dividing by zero — the parser degrades to text-only heuristics.
    if (imageHeight <= 0) return 0f
    val centre = (boxTop + boxBottom) / 2f
    return (centre / imageHeight).coerceIn(0f, 1f)
}
