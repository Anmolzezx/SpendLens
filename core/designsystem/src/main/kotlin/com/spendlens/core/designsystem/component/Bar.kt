package com.spendlens.core.designsystem.component

/**
 * One column of a [BarChart].
 *
 * @param fraction height as a share of the tallest bar, 0f..1f.
 * @param emphasised drawn in the accent colour — one bar the reader should find first, such as the
 *   month they are in.
 * @param contentDescription what TalkBack reads for this bar. Required: a rectangle says nothing to a
 *   screen reader, and the caller is the only one that knows what the bar means.
 */
data class Bar(
    val label: String,
    val fraction: Float,
    val emphasised: Boolean,
    val contentDescription: String,
)
