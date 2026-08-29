package com.spendlens.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme

/**
 * The placeholder for any screen with nothing to show.
 *
 * Both the "no expenses yet" and the "no results for these filters" cases use this, and they are not
 * the same screen: the first offers an action, the second offers a way back. Passing different copy
 * is the whole difference, which is why [actionLabel] and [onAction] are optional.
 *
 * Note `heightIn`/`widthIn` rather than fixed sizes — at a 200% font scale a fixed height clips the
 * description, which is exactly what the phase 5 accessibility pass looks for.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinHeight)
            .padding(Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            modifier = Modifier.widthIn(max = MaxTextWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = Spacing.Small)
                    .heightIn(min = Spacing.MinTouchTarget),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

private val MinHeight = 240.dp
private val MaxTextWidth = 320.dp

@ThemePreviews
@Composable
private fun EmptyStateFirstRunPreview() {
    SpendLensTheme {
        Surface {
            EmptyState(
                title = "No expenses yet",
                description = "Scan a receipt or add one by hand — everything works offline.",
                actionLabel = "Scan a receipt",
                onAction = {},
            )
        }
    }
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun EmptyStateFilteredPreview() {
    SpendLensTheme {
        Surface {
            // No action: the user has results elsewhere, they just filtered them all out.
            EmptyState(
                title = "Nothing matches those filters",
                description = "Try widening the date range or clearing a category.",
            )
        }
    }
}
