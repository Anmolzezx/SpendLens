package com.spendlens.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme

/**
 * A row of vertical bars with labels underneath.
 *
 * Hand-drawn on [Canvas], like `MeterBar` and the proportion bar: a charting library renders a hundred
 * chart types, and this app needs one.
 *
 * Each bar is its own semantics node, unlike the proportion bar whose numbers are repeated by the rows
 * beneath it. Here the bars are the only place the numbers appear.
 *
 * No track behind the bars. Seen on a device: a full-height track made five empty months look like five
 * full bars with a sliver at the bottom, which reads as "much the same every month" — the opposite of
 * what the data said. Height alone carries the meaning now.
 *
 * A bar with a zero fraction still draws [MIN_BAR_FRACTION] of the height, so an empty month reads as
 * "nothing here" rather than as a missing column.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    barHeight: Dp = ChartHeight,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = bar.contentDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                BarColumn(bar = bar, height = barHeight)
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BarColumn(
    bar: Bar,
    height: Dp,
) {
    val barColor = if (bar.emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.width / 4f)
        val filled = size.height * bar.fraction.coerceIn(MIN_BAR_FRACTION, 1f)
        drawRoundRect(
            color = barColor,
            topLeft = Offset(0f, size.height - filled),
            size = Size(size.width, filled),
            cornerRadius = radius,
        )
    }
}

/** Enough to be visible as a bar at zero without reading as a real amount. */
private const val MIN_BAR_FRACTION = 0.02f

private val ChartHeight = 96.dp

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun BarChartPreview() {
    SpendLensTheme {
        Surface {
            BarChart(
                bars = listOf(
                    Bar("Apr", 0.4f, emphasised = false, contentDescription = "April, $400"),
                    Bar("May", 0f, emphasised = false, contentDescription = "May, nothing spent"),
                    Bar("Jun", 0.75f, emphasised = false, contentDescription = "June, $750"),
                    Bar("Jul", 1f, emphasised = false, contentDescription = "July, $1,000"),
                    Bar("Aug", 0.6f, emphasised = false, contentDescription = "August, $600"),
                    Bar("Sep", 0.2f, emphasised = true, contentDescription = "September, $200"),
                ),
                modifier = Modifier.padding(Spacing.Large),
            )
        }
    }
}
