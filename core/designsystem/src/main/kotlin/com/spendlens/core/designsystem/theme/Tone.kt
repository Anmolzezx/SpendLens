package com.spendlens.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The bridge between domain meaning and [SemanticColors].
 *
 * This module deliberately knows nothing about `SyncState` or `BudgetStatus` — keeping it free of
 * domain types is what lets it be rendered on the JVM with no dependencies. Instead, feature modules
 * map their own enums onto a [Tone], and the design system decides what each tone looks like in
 * light and dark.
 */
enum class Tone {
    NEUTRAL,
    POSITIVE,
    WARNING,
    CRITICAL,
}

@Composable
@ReadOnlyComposable
fun Tone.color(): Color = when (this) {
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.POSITIVE -> MaterialTheme.semantic.budgetUnder
    Tone.WARNING -> MaterialTheme.semantic.budgetNear
    Tone.CRITICAL -> MaterialTheme.semantic.budgetOver
}
