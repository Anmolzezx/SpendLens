package com.spendlens.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.amounts

/**
 * Displays a pre-formatted money string in tabular figures.
 *
 * Takes the formatted [amount] rather than a `Long` and a currency code on purpose: formatting is
 * locale-aware domain logic that lives in `core:model`, and this module must stay domain-free.
 */
@Composable
fun AmountText(
    amount: String,
    modifier: Modifier = Modifier,
    emphasis: AmountEmphasis = AmountEmphasis.MEDIUM,
    color: Color = Color.Unspecified,
) {
    Text(
        text = amount,
        modifier = modifier,
        color = color,
        style = when (emphasis) {
            AmountEmphasis.LARGE -> MaterialTheme.amounts.large
            AmountEmphasis.MEDIUM -> MaterialTheme.amounts.medium
            AmountEmphasis.SMALL -> MaterialTheme.amounts.small
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun AmountTextPreview() {
    SpendLensTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                // Same digit count, different digits: with tabular figures these stay
                // perfectly aligned. Swap the style to a proportional one to see them drift.
                AmountText("$2,400.00", emphasis = AmountEmphasis.LARGE)
                AmountText("$1,111.11", emphasis = AmountEmphasis.LARGE)
                AmountText("$12.34", emphasis = AmountEmphasis.MEDIUM)
                AmountText("$1.99", emphasis = AmountEmphasis.SMALL)
            }
        }
    }
}
