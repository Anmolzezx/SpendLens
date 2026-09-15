package com.spendlens.feature.expenses.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.Spacing
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.model.Category
import com.spendlens.feature.expenses.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseEditContent(
    uiState: ExpenseEditUiState,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNewExpense) {
                                R.string.expense_edit_title_new
                            } else {
                                R.string.expense_edit_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.expense_edit_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.expense_edit_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            OutlinedTextField(
                value = uiState.merchant,
                onValueChange = onMerchantChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expense_edit_merchant)) },
                singleLine = true,
                isError = uiState.showErrors && uiState.merchantError != null,
                supportingText = uiState.merchantError.supportingText(uiState.showErrors),
            )

            OutlinedTextField(
                value = uiState.amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expense_edit_amount)) },
                singleLine = true,
                // Decimal keyboard: the parser accepts one separator and rejects grouping, which is
                // exactly what this keyboard can produce.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = uiState.showErrors && uiState.amountError != null,
                supportingText = uiState.amountError.supportingText(uiState.showErrors),
            )

            CategoryPicker(
                categories = uiState.categories,
                selectedId = uiState.categoryId,
                onSelect = onCategoryChange,
                isError = uiState.showErrors && uiState.categoryError != null,
            )

            DateField(
                occurredOn = uiState.occurredOn,
                onClick = { showDatePicker = true },
            )

            OutlinedTextField(
                value = uiState.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expense_edit_note)) },
                minLines = 3,
            )
        }
    }

    if (showDatePicker) {
        // Material's DatePicker represents a date as **UTC midnight** in epoch milliseconds. Both
        // directions therefore convert in UTC. Using the device zone instead is the classic bug: west
        // of Greenwich, UTC midnight on the 26th is the evening of the 25th, and the picked date
        // saves one day early. The previous Instant-based version of this screen had exactly that.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.occurredOn
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.expense_edit_date_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.expense_edit_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = stringResource(R.string.expense_edit_category),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
        if (isError) {
            Text(
                text = stringResource(R.string.expense_edit_error_required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DateField(
    occurredOn: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = stringResource(R.string.expense_edit_date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClick) {
            Text(formatDate(occurredOn))
        }
    }
}

@Composable
private fun formatDate(date: LocalDate): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(date)

/**
 * Errors are only shown once the user has attempted to save. Flagging "required" on a field they
 * have not reached yet reads as the form shouting at them for not having finished typing.
 */
@Composable
private fun FieldError?.supportingText(show: Boolean): (@Composable () -> Unit)? {
    if (!show || this == null) return null
    val messageRes = when (this) {
        FieldError.REQUIRED -> R.string.expense_edit_error_required
        FieldError.NOT_A_NUMBER -> R.string.expense_edit_error_not_a_number
        FieldError.TOO_PRECISE -> R.string.expense_edit_error_too_precise
        FieldError.NEGATIVE -> R.string.expense_edit_error_negative
        FieldError.TOO_LARGE -> R.string.expense_edit_error_too_large
    }
    return { Text(stringResource(messageRes)) }
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun ExpenseEditNewPreview() {
    SpendLensTheme {
        Surface {
            ExpenseEditContent(
                uiState = ExpenseEditPreviewData.blank,
                onMerchantChange = {},
                onAmountChange = {},
                onCategoryChange = {},
                onNoteChange = {},
                onDateChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseEditFilledPreview() {
    SpendLensTheme {
        Surface {
            ExpenseEditContent(
                uiState = ExpenseEditPreviewData.filled,
                onMerchantChange = {},
                onAmountChange = {},
                onCategoryChange = {},
                onNoteChange = {},
                onDateChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ExpenseEditErrorsPreview() {
    SpendLensTheme {
        Surface {
            // Save attempted with an unparseable amount and nothing else filled in.
            ExpenseEditContent(
                uiState = ExpenseEditPreviewData.withErrors,
                onMerchantChange = {},
                onAmountChange = {},
                onCategoryChange = {},
                onNoteChange = {},
                onDateChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}
