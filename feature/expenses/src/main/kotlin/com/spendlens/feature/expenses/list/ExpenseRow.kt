package com.spendlens.feature.expenses.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.animation.sharedBoundsWith
import com.spendlens.core.designsystem.component.AmountEmphasis
import com.spendlens.core.designsystem.component.AmountText
import com.spendlens.core.designsystem.component.CategoryChip
import com.spendlens.core.designsystem.component.StatusBadge
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.model.SyncState
import com.spendlens.feature.expenses.R
import com.spendlens.feature.expenses.amountSharedKey
import com.spendlens.feature.expenses.merchantSharedKey

@Composable
internal fun ExpenseRow(
    expense: ExpenseUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 48dp is the Material minimum touch target; the row is taller in practice, but the
            // floor guarantees it even at the smallest font scale.
            .heightIn(min = Spacing.MinTouchTarget)
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = expense.merchant,
                modifier = Modifier.sharedBoundsWith(merchantSharedKey(expense.id)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryChip(
                    label = expense.categoryName ?: stringResource(R.string.expenses_uncategorised),
                    colorIndex = expense.categoryColorIndex,
                )
                Text(
                    text = secondaryLine(expense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            AmountText(
                amount = expense.formattedAmount,
                modifier = Modifier.sharedBoundsWith(amountSharedKey(expense.id)),
                emphasis = AmountEmphasis.MEDIUM,
                color = MaterialTheme.colorScheme.onSurface,
            )
            expense.syncState.badge()?.let { (label, tone) ->
                StatusBadge(label = label, tone = tone)
            }
        }
    }
}

@Composable
private fun secondaryLine(expense: ExpenseUiModel): String =
    if (expense.hasReceipt) {
        "${expense.formattedDate} · ${stringResource(R.string.expenses_receipt)}"
    } else {
        expense.formattedDate
    }

/**
 * Maps the domain enum onto the design system's [Tone].
 *
 * `SYNCED` returns null on purpose: the common case needs no badge, and a row of green "Synced"
 * chips is noise that makes the two states that *do* need attention harder to spot.
 */
@Composable
private fun SyncState.badge(): Pair<String, Tone>? =
    when (this) {
        SyncState.SYNCED -> null
        SyncState.PENDING -> stringResource(R.string.expenses_sync_pending) to Tone.WARNING
        SyncState.CONFLICT -> stringResource(R.string.expenses_sync_conflict) to Tone.CRITICAL
    }

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun ExpenseRowPreview() {
    SpendLensTheme {
        Surface {
            Column {
                ExpenseRow(expense = ExpenseListPreviewData.expenses[0], onClick = {})
                // 44-character merchant name — checks truncation.
                ExpenseRow(expense = ExpenseListPreviewData.expenses[1], onClick = {})
                // Largest amount, and the conflicted row.
                ExpenseRow(expense = ExpenseListPreviewData.expenses[5], onClick = {})
            }
        }
    }
}
