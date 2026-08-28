package com.spendlens.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Generated from the seed colour #006A6B with Material Theme Builder. Teal reads as "money"
// without being literally green, and leaves red free to mean only one thing: over budget.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF6FC),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF4CDADE),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF4CDADE),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FC),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF191C1C),
    inversePrimary = Color(0xFF00696D),
    scrim = Color(0xFF000000),
)

/**
 * The colours Material 3 does not have a role for.
 *
 * `ColorScheme` has no "over budget", "sync pending", or "category 4". The usual workaround is a
 * handful of top-level `val`s, which then do not respond to dark mode. Putting them behind a
 * [CompositionLocal][androidx.compose.runtime.CompositionLocal] instead means they swap with the
 * theme exactly like the built-in roles do.
 *
 * `@Immutable` is a promise to the Compose compiler that nothing in here changes after construction,
 * which keeps composables that read these colours skippable.
 */
@Immutable
data class SemanticColors(
    val budgetUnder: Color,
    val budgetNear: Color,
    val budgetOver: Color,
    val syncPending: Color,
    val syncSynced: Color,
    val syncConflict: Color,
    val onSemanticContainer: Color,
    val categoryPalette: ImmutableList<Color>,
) {
    /** Categories store an index, not a colour, so the palette wraps rather than crashing. */
    fun categoryColor(index: Int): Color = categoryPalette[index.mod(categoryPalette.size)]
}

internal val LightSemanticColors = SemanticColors(
    budgetUnder = Color(0xFF16651F),
    budgetNear = Color(0xFF8A5000),
    budgetOver = Color(0xFFBA1A1A),
    syncPending = Color(0xFF8A5000),
    syncSynced = Color(0xFF16651F),
    syncConflict = Color(0xFFBA1A1A),
    onSemanticContainer = Color(0xFF191C1C),
    categoryPalette = persistentListOf(
        Color(0xFF00696D),
        Color(0xFF7B4E7F),
        Color(0xFF8A5000),
        Color(0xFF16651F),
        Color(0xFF4B607C),
        Color(0xFFA03D3D),
    ),
)

internal val DarkSemanticColors = SemanticColors(
    budgetUnder = Color(0xFF7FDA85),
    budgetNear = Color(0xFFFFB95C),
    budgetOver = Color(0xFFFFB4AB),
    syncPending = Color(0xFFFFB95C),
    syncSynced = Color(0xFF7FDA85),
    syncConflict = Color(0xFFFFB4AB),
    onSemanticContainer = Color(0xFFE0E3E2),
    categoryPalette = persistentListOf(
        Color(0xFF4CDADE),
        Color(0xFFEBB4EE),
        Color(0xFFFFB95C),
        Color(0xFF7FDA85),
        Color(0xFFB3C8E8),
        Color(0xFFFFB4AB),
    ),
)

/**
 * `staticCompositionLocalOf`, not `compositionLocalOf`: this value only changes on a whole-theme
 * switch, so the cheaper variant that skips per-read tracking is the right one. The trade-off is that
 * a change recomposes everything under the provider — which is exactly what a theme switch wants.
 */
val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
