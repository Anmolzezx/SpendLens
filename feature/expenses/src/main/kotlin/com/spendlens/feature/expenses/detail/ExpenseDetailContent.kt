package com.spendlens.feature.expenses.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.SubcomposeAsyncImage
import com.spendlens.core.designsystem.component.AmountEmphasis
import com.spendlens.core.designsystem.component.AmountText
import com.spendlens.core.designsystem.component.CategoryChip
import com.spendlens.core.designsystem.component.EmptyState
import com.spendlens.core.designsystem.component.StatusBadge
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.model.SyncState
import com.spendlens.feature.expenses.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseDetailContent(
    uiState: ExpenseDetailUiState,
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(stringResource(R.string.expense_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            ExpenseDetailUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            ExpenseDetailUiState.NotFound -> EmptyState(
                title = stringResource(R.string.expense_detail_not_found_title),
                description = stringResource(R.string.expense_detail_not_found_description),
                modifier = Modifier.padding(padding),
                actionLabel = stringResource(R.string.expense_detail_back),
                onAction = onBack,
            )

            is ExpenseDetailUiState.Success -> ExpenseDetail(
                expense = uiState.expense,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun ExpenseDetail(
    expense: ExpenseDetailUiModel,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Scrollable even though it fits today: at a 200% font scale with a long note it will not.
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(
                text = expense.merchant,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AmountText(
                amount = expense.formattedAmount,
                emphasis = AmountEmphasis.LARGE,
                color = MaterialTheme.colorScheme.onSurface,
            )
            expense.syncState.badge()?.let { (label, tone) ->
                StatusBadge(label = label, tone = tone)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DetailRow(label = stringResource(R.string.expense_detail_date)) {
            Text(
                text = expense.formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        DetailRow(label = stringResource(R.string.expense_detail_category)) {
            CategoryChip(
                label = expense.categoryName ?: stringResource(R.string.expenses_uncategorised),
                colorIndex = expense.categoryColorIndex,
            )
        }

        if (expense.note != null) {
            DetailRow(label = stringResource(R.string.expense_detail_note)) {
                Text(
                    text = expense.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        DetailRow(label = stringResource(R.string.expense_detail_receipt)) {
            if (expense.receiptImage != null) {
                ReceiptImage(file = expense.receiptImage)
            } else {
                Text(
                    text = stringResource(R.string.expense_detail_no_receipt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            TextButton(onClick = { onEditClick(expense.id) }) {
                Text(stringResource(R.string.expense_detail_edit))
            }
            TextButton(onClick = { onDeleteClick(expense.id) }) {
                Text(
                    text = stringResource(R.string.expense_detail_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The stored receipt photo.
 *
 * `SubcomposeAsyncImage` for its loading and error slots. Its subcomposition cost is real in a
 * scrolling list, but this is one image on one screen.
 *
 * The error state is not hypothetical: the row can outlive its file — storage cleared from Settings,
 * or a restore that brought back the database without `filesDir`. A missing photo must say so rather
 * than leave an empty grey box the user can only guess about.
 *
 * `ContentScale.Fit`, not `Crop`: cropping a receipt can cut off the total, which is the one line the
 * user opened this to read.
 */
@Composable
private fun ReceiptImage(
    file: File,
    modifier: Modifier = Modifier,
) {
    val frame = modifier
        .fillMaxWidth()
        .aspectRatio(RECEIPT_ASPECT_RATIO)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    SubcomposeAsyncImage(
        model = file,
        contentDescription = stringResource(R.string.expense_detail_receipt_photo),
        modifier = frame,
        contentScale = ContentScale.Fit,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.expense_detail_receipt_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SyncState.badge(): Pair<String, Tone>? =
    when (this) {
        SyncState.SYNCED -> null
        SyncState.PENDING -> stringResource(R.string.expenses_sync_pending) to Tone.WARNING
        SyncState.CONFLICT -> stringResource(R.string.expenses_sync_conflict) to Tone.CRITICAL
    }

/**
 * Portrait, matching both the shape of a receipt and a phone camera's capture. A landscape frame put
 * the real capture between two grey bars that took up most of the box.
 */
private const val RECEIPT_ASPECT_RATIO = 3f / 4f

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun ExpenseDetailSuccessPreview() {
    SpendLensTheme {
        Surface {
            ExpenseDetailContent(
                uiState = ExpenseDetailPreviewData.withReceiptAndNote,
                onBack = {},
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseDetailMinimalPreview() {
    SpendLensTheme {
        Surface {
            // No note, no receipt — the rows that must not leave empty gaps behind.
            ExpenseDetailContent(
                uiState = ExpenseDetailPreviewData.minimal,
                onBack = {},
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseDetailNotFoundPreview() {
    SpendLensTheme {
        Surface {
            ExpenseDetailContent(
                uiState = ExpenseDetailUiState.NotFound,
                onBack = {},
                onEditClick = {},
                onDeleteClick = {},
            )
        }
    }
}
