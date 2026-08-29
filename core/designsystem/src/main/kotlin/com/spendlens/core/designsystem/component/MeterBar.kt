package com.spendlens.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.designsystem.theme.color

/**
 * A progress meter for budgets.
 *
 * [stateDescription] is required rather than derived: the caller knows the real numbers, and a
 * screen reader needs "Groceries, $278 of $300 spent, nearing budget" — not "92 percent". The bar
 * itself is cleared from the semantics tree so TalkBack announces that sentence once, instead of the
 * sentence plus a redundant progress-bar node.
 *
 * [fraction] is coerced, so a category that is 197% of budget renders as a full bar instead of
 * overflowing its track.
 */
@Composable
fun MeterBar(
    fraction: Float,
    tone: Tone,
    stateDescription: String,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clearAndSetSemantics { contentDescription = stateDescription },
        color = tone.color(),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

private val MeterHeight = 8.dp

@ThemePreviews
@Composable
private fun MeterBarPreview() {
    SpendLensTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
            ) {
                MeterBar(
                    fraction = 0.567f,
                    tone = Tone.POSITIVE,
                    stateDescription = "Health, $226 of $400 spent, under budget",
                )
                MeterBar(
                    fraction = 0.927f,
                    tone = Tone.WARNING,
                    stateDescription = "Groceries, $278 of $300 spent, nearing budget",
                )
                // 197% of budget — coerced to a full bar rather than overflowing.
                MeterBar(
                    fraction = 1.978f,
                    tone = Tone.CRITICAL,
                    stateDescription = "Transport, $1,977 of $1,000 spent, over budget",
                )
            }
        }
    }
}
