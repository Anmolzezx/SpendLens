package com.spendlens.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a preview in both colour schemes. Every component in this module carries one, so a
 * contrast mistake shows up in the IDE rather than three screens later on a device.
 */
@Preview(name = "light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class ThemePreviews

/**
 * Both schemes plus a 200% font scale. Use it on anything with text in a constrained box — fixed
 * heights and single-line assumptions fail here first, which is the point.
 */
@ThemePreviews
@Preview(name = "large font", fontScale = 2.0f, showBackground = true)
annotation class FullPreviews
