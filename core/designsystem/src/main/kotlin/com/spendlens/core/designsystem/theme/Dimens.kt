package com.spendlens.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap and inset in the app comes from here, so "a bit more padding" is a
 * decision made once rather than a stray `13.dp` in one screen.
 *
 * [MinTouchTarget] is not decoration: 48dp is the Material accessibility minimum, and the phase 5
 * accessibility pass checks it. Anything tappable gets at least this.
 */
object Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val Huge = 32.dp

    val MinTouchTarget = 48.dp
}
