package com.spendlens.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.theme.semantic

/**
 * A single horizontal bar split into one segment per category, sized by share of the month's spend.
 *
 * Hand-rolled on [Canvas] rather than pulled from a charting library: one bar with rounded outer
 * corners is a dozen lines of drawing code, and a dependency that renders a hundred chart types is a
 * poor trade for it.
 *
 * Segments below [MIN_VISIBLE_SHARE] are dropped rather than drawn as slivers — a half-pixel stripe
 * reads as a rendering artefact, and rounding several of them up would make the segments stop
 * summing to the whole.
 *
 * The whole bar is one semantics node with [stateDescription]; the per-category numbers are already
 * announced by the rows beneath it, so letting TalkBack walk the segments would just repeat them.
 */
@Composable
internal fun SpendProportionBar(
    segments: List<Pair<Int, Float>>,
    stateDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = BarHeight,
) {
    val palette = MaterialTheme.semantic
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val visible = segments.filter { (_, share) -> share >= MIN_VISIBLE_SHARE }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = stateDescription },
    ) {
        val radius = CornerRadius(size.height / 2f)
        // The track fills any remainder, so a month that is only partly categorised still reads as
        // a complete bar rather than one that stops short for no visible reason.
        drawRoundRect(color = trackColor, cornerRadius = radius)

        var startX = 0f
        visible.forEach { (colorIndex, share) ->
            val segmentWidth = size.width * share
            drawRoundRect(
                color = palette.categoryColor(colorIndex),
                topLeft = Offset(startX, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = radius,
            )
            startX += segmentWidth + SegmentGap.toPx()
        }
    }
}

private val BarHeight = 12.dp
private val SegmentGap = 2.dp

/** Below roughly 1.5% a segment is a sliver, and a sliver reads as a glitch. */
private const val MIN_VISIBLE_SHARE = 0.015f

/** Exposed for the preview and for tests that build segment lists. */
internal fun segmentsOf(categories: List<CategoryInsightUiModel>): List<Pair<Int, Float>> =
    categories.map { it.colorIndex to it.shareOfTotal }
