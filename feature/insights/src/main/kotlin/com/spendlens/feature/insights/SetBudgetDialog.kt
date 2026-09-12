package com.spendlens.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.spendlens.core.designsystem.preview.ThemePreviews
import com.spendlens.core.designsystem.theme.SpendLensTheme
import com.spendlens.core.model.MoneyParseResult
import com.spendlens.core.model.parseMoney

/**
 * Sets or clears one category's monthly limit.
 *
 * Reuses `parseMoney` rather than trusting the keyboard: a decimal keypad still allows "12.345",
 * and the currency decides how many places are legal — two for USD, none for JPY.
 */
@Composable
internal fun SetBudgetDialog(
    categoryName: String,
    currency: String,
    initialAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    val parsed = parseMoney(amount, currency)
    val error = when (parsed) {
        is MoneyParseResult.Success -> null
        MoneyParseResult.Empty -> null // Empty is "clear the budget", not a mistake.
        MoneyParseResult.NotANumber -> R.string.insights_budget_error_not_a_number
        MoneyParseResult.TooPrecise -> R.string.insights_budget_error_too_precise
        MoneyParseResult.Negative -> R.string.insights_budget_error_negative
        MoneyParseResult.TooLarge -> R.string.insights_budget_error_too_large
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.insights_budget_title, categoryName)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.insights_budget_monthly_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(stringResource(it)) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (parsed) {
                        is MoneyParseResult.Success -> onConfirm(parsed.amountMinor)
                        MoneyParseResult.Empty -> onClear()
                        else -> Unit
                    }
                },
                enabled = error == null,
            ) {
                Text(stringResource(R.string.insights_budget_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.insights_budget_cancel))
            }
        },
    )
}

@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
@Composable
private fun SetBudgetDialogPreview() {
    SpendLensTheme {
        SetBudgetDialog(
            categoryName = "Groceries",
            currency = "USD",
            initialAmount = "300.00",
            onDismiss = {},
            onConfirm = {},
            onClear = {},
        )
    }
}
