package com.spendlens.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val SpendLensTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

/**
 * Styles for money.
 *
 * `tnum` is the OpenType tabular-figures feature: every digit gets the same advance width, so amounts
 * line up in a column and a running total does not shuffle sideways as it animates. Proportional
 * figures — the default — make a list of prices look subtly crooked, and it is the sort of detail
 * that separates an app that was designed from one that was assembled.
 */
@Immutable
data class AmountTextStyles(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
)

internal val SpendLensAmountStyles = AmountTextStyles(
    large = SpendLensTypography.headlineMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
    medium = SpendLensTypography.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
    small = SpendLensTypography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
)

private const val TABULAR_FIGURES = "tnum"

val LocalAmountTextStyles = staticCompositionLocalOf { SpendLensAmountStyles }
