package com.spendlens.core.model

/**
 * A spending category.
 *
 * [colorIndex] and [iconKey] are deliberately indirect. This module must not depend on Compose, so it
 * cannot hold a `Color` or an `ImageVector` — and it should not want to: storing a raw colour would
 * pin the category to one theme, and the same index resolves to a different (correctly contrasting)
 * colour in light and dark mode. The design system owns the palette; the model owns the choice.
 */
data class Category(
    val id: String,
    val name: String,
    /** Index into the design system's category palette; wraps if it exceeds the palette size. */
    val colorIndex: Int,
    /** Stable key the UI maps to an icon, e.g. "cart". Never a drawable resource id. */
    val iconKey: String,
)
