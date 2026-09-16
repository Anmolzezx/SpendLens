package com.spendlens.core.designsystem.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The two scopes a shared element needs, carried from where they exist to where they are used.
 *
 * `SharedTransitionLayout` has to wrap the whole `NavHost`, which lives in `:app`; the
 * `AnimatedVisibilityScope` belongs to one destination and comes from each `composable { }` block in a
 * feature's own graph. Threading both through every screen's signature would put animation parameters
 * on composables that do not animate, so they travel as composition locals instead.
 *
 * Null when nothing provides them — a preview, a screenshot test, or a screen reached outside a
 * transition. Elements then simply draw without animating.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as the same thing as the element with the same [key] on the other screen, so it
 * flies between the two instead of one fading out while the other fades in.
 *
 * `sharedBounds`, not `sharedElement`: the two are the same *thing* rather than the same pixels — a
 * merchant name is a list row's title at one end and a headline at the other — so the bounds animate
 * while the content cross-fades, rather than one text being stretched into the other's size.
 *
 * Returns the modifier untouched when either scope is missing, which is what makes previews and tests
 * work without a `SharedTransitionLayout` around them.
 */
@Composable
fun Modifier.sharedBoundsWith(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this

    return with(sharedScope) {
        // The default resize mode scales the content to the animating bounds, which is what makes a
        // list row's title read as the same thing as a headline twice its size.
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
