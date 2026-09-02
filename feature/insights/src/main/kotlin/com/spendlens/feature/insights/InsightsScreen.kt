package com.spendlens.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.YearMonth
import java.time.ZoneId

/**
 * Stateful half of the insights screen.
 *
 * Pinned to the month the sample fixtures occupy rather than `YearMonth.now()`. With static data,
 * "this month" would show an empty screen from September onwards — and the emptiness would look
 * like a bug rather than the absence of fixtures. The real ViewModel will use the current month.
 */
@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    val uiState = remember(locale, context) {
        FakeInsights.success(
            month = SAMPLE_MONTH,
            zoneId = ZoneId.systemDefault(),
            locale = locale,
            strings = ResourceStrings(context),
        )
    }

    InsightsContent(uiState = uiState, modifier = modifier)
}

private val SAMPLE_MONTH: YearMonth = YearMonth.of(2026, 8)
