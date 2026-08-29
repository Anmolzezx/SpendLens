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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.designsystem.theme.color

/**
 * A small pill carrying a status — sync state, budget state, anything with a tone.
 *
 * The [label] is required, and that is the point: colour must never be the only carrier of meaning.
 * Roughly 1 in 12 men has some form of colour vision deficiency, and TalkBack cannot read a colour at
 * all. A coloured dot on its own communicates nothing to either.
 */
@Composable
fun StatusBadge(
    label: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    val toneColor = tone.color()
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
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(toneColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val DotSize = 8.dp

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun StatusBadgePreview() {
    SpendLensTheme {
        Surface {
            Row(
                modifier = Modifier.padding(Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                StatusBadge(label = "Synced", tone = Tone.POSITIVE)
                StatusBadge(label = "Pending", tone = Tone.WARNING)
                StatusBadge(label = "Conflict", tone = Tone.CRITICAL)
            }
        }
    }
}
