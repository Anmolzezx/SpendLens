package com.spendlens.core.ocr

import kotlin.math.abs
import kotlin.math.min

/**
 * Rebuilds printed rows from ML Kit's lines.
 *
 * ML Kit reports text that is far apart horizontally as separate lines, even on the same printed row.
 * A receipt is laid out exactly that way — "TOTAL" at the left margin, "21.22" right-aligned — so
 * without this the parser sees a label with no amount and an amount with no label.
 *
 * Two lines belong to the same row when their vertical centres are closer than half the shorter line's
 * height. Half a line height is tolerant of a slightly skewed photo, but tight enough that the next
 * row down — a full line height away — never merges.
 */
internal fun assembleRows(lines: List<RecognizedLine>): List<RecognizedLine> {
    val rows = mutableListOf<MutableList<RecognizedLine>>()

    lines.sortedBy { it.verticalPosition }.forEach { line ->
        val row = rows.lastOrNull()
        if (row != null && row.first().sharesRowWith(line)) {
            row += line
        } else {
            rows += mutableListOf(line)
        }
    }

    return rows.map { row ->
        if (row.size == 1) {
            row.single()
        } else {
            RecognizedLine(
                text = row.sortedBy { it.horizontalPosition }.joinToString(" ") { it.text.trim() },
                verticalPosition = row.map { it.verticalPosition }.average().toFloat(),
                horizontalPosition = row.minOf { it.horizontalPosition },
                heightFraction = row.maxOf { it.heightFraction },
            )
        }
    }
}

private fun RecognizedLine.sharesRowWith(other: RecognizedLine): Boolean {
    // No geometry, no merging: text from a transcript has nothing to say about rows.
    if (heightFraction <= 0f || other.heightFraction <= 0f) return false
    val tolerance = min(heightFraction, other.heightFraction) / 2
    return abs(verticalPosition - other.verticalPosition) < tolerance
}
