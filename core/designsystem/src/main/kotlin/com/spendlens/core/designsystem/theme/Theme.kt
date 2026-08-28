package com.spendlens.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * @param dynamicColor opt in to Material You wallpaper colours. Defaults to `false` so previews and
 *   Paparazzi renders are deterministic; `:app` turns it on from the user's setting.
 */
@Composable
fun SpendLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    // Deliberately NOT derived from dynamic colour. If the user's wallpaper is red, "under budget"
    // must not turn red. Material You reskins the chrome; the colours that carry meaning stay put.
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalAmountTextStyles provides SpendLensAmountStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SpendLensTypography,
            shapes = SpendLensShapes,
            content = content,
        )
    }
}

/** Reads as `MaterialTheme.semantic.budgetOver` — same ergonomics as the built-in M3 roles. */
val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

/** Reads as `MaterialTheme.amounts.large`. */
val MaterialTheme.amounts: AmountTextStyles
    @Composable
    @ReadOnlyComposable
    get() = LocalAmountTextStyles.current
