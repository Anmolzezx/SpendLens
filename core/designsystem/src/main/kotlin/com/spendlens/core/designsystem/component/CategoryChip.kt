package com.spendlens.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.semantic

/**
 * A category label with its palette colour.
 *
 * Takes a [colorIndex] rather than a `Color` because that is what the domain model stores — and it is
 * what makes categories re-theme correctly, since index 0 resolves to a dark-friendly teal in dark
 * mode and a light-friendly one in light mode. The index wraps, so a seventh category cannot crash
 * a six-colour palette.
 */
@Composable
fun CategoryChip(
    label: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
) {
    val categoryColor = MaterialTheme.semantic.categoryColor(colorIndex)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.Small,
                vertical = Spacing.ExtraSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SwatchSize)
                    .clip(CircleShape)
                    .background(categoryColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SwatchSize = 10.dp

@ThemePreviews
@Composable
private fun CategoryChipPreview() {
    SpendLensTheme {
        Surface {
            Row(
                modifier = Modifier.padding(Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                CategoryChip(label = "Groceries", colorIndex = 0)
                CategoryChip(label = "Dining Out", colorIndex = 1)
                // Index 7 against a 6-colour palette: wraps rather than crashing.
                CategoryChip(label = "Wrapped", colorIndex = 7)
            }
        }
    }
}
