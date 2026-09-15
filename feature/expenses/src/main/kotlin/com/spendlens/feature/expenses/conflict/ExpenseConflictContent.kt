package com.spendlens.feature.expenses.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.component.CategoryChip
import com.spendlens.core.designsystem.component.StatusBadge
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.designsystem.theme.Tone
import com.spendlens.core.model.ExpenseField
import com.spendlens.feature.expenses.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseConflictContent(
    uiState: ExpenseConflictUiState,
    onBack: () -> Unit,
    onKeepThisDevice: () -> Unit,
    onKeepOtherDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_conflict_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(stringResource(R.string.expense_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            // Resolved shows the spinner for the instant before the screen closes, rather than a flash
            // of an empty page.
            ExpenseConflictUiState.Loading, ExpenseConflictUiState.Resolved -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is ExpenseConflictUiState.Deciding -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
            ) {
                Text(
                    text = stringResource(R.string.expense_conflict_explanation),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VersionCard(
                    title = stringResource(R.string.expense_conflict_this_device),
                    version = uiState.conflict.thisDevice,
                    differences = uiState.conflict.differences,
                    enabled = !uiState.isSaving,
                    onKeep = onKeepThisDevice,
                )
                VersionCard(
                    title = stringResource(R.string.expense_conflict_other_device),
                    version = uiState.conflict.otherDevice,
                    differences = uiState.conflict.differences,
                    enabled = !uiState.isSaving,
                    onKeep = onKeepOtherDevice,
                )
            }
        }
    }
}

/**
 * One version, with the fields that differ from the other version marked.
 *
 * Marked with a labelled badge, not only a colour, so the difference is readable without colour
 * vision and announced by TalkBack.
 */
@Composable
private fun VersionCard(
    title: String,
    version: ExpenseVersionUiModel,
    differences: Set<ExpenseField>,
    enabled: Boolean,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.expense_conflict_edited_at, version.formattedEditedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (version.isDeleted) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    StatusBadge(label = stringResource(R.string.expense_conflict_deleted), tone = Tone.CRITICAL)
                }
                Text(
                    text = stringResource(R.string.expense_conflict_deleted_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Field(R.string.expense_edit_merchant, ExpenseField.MERCHANT in differences) {
                    FieldText(version.merchant, ExpenseField.MERCHANT in differences)
                }
                Field(R.string.expense_edit_amount, ExpenseField.AMOUNT in differences) {
                    FieldText(version.formattedAmount, ExpenseField.AMOUNT in differences)
                }
                Field(R.string.expense_detail_date, ExpenseField.DATE in differences) {
                    FieldText(version.formattedDate, ExpenseField.DATE in differences)
                }
                Field(R.string.expense_detail_category, ExpenseField.CATEGORY in differences) {
                    CategoryChip(
                        label = version.categoryName ?: stringResource(R.string.expenses_uncategorised),
                        colorIndex = version.categoryColorIndex,
                    )
                }
                Field(R.string.expense_detail_note, ExpenseField.NOTE in differences) {
                    FieldText(
                        text = version.note ?: stringResource(R.string.expense_conflict_no_note),
                        isDifferent = ExpenseField.NOTE in differences,
                        isPlaceholder = version.note == null,
                    )
                }
            }

            Button(onClick = onKeep, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.expense_conflict_keep))
            }
        }
    }
}

@Composable
private fun Field(
    labelRes: Int,
    isDifferent: Boolean,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDifferent) {
                StatusBadge(label = stringResource(R.string.expense_conflict_different), tone = Tone.WARNING)
            }
        }
        content()
    }
}

/**
 * @param isPlaceholder the text stands in for a missing value ("No note"). It is muted and never
 *   emboldened: seen on the emulator, a bold "No note" read as though someone had typed those words.
 */
@Composable
private fun FieldText(
    text: String,
    isDifferent: Boolean,
    isPlaceholder: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        // Weight as well as the badge: the eye lands on what changed before it reads the labels.
        fontWeight = if (isDifferent && !isPlaceholder) FontWeight.SemiBold else null,
        color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun ExpenseConflictEditedPreview() {
    SpendLensTheme {
        Surface {
            ExpenseConflictContent(
                uiState = ExpenseConflictPreviewData.editedOnBoth,
                onBack = {},
                onKeepThisDevice = {},
                onKeepOtherDevice = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseConflictDeletedPreview() {
    SpendLensTheme {
        Surface {
            ExpenseConflictContent(
                uiState = ExpenseConflictPreviewData.deletedHere,
                onBack = {},
                onKeepThisDevice = {},
                onKeepOtherDevice = {},
            )
        }
    }
}
