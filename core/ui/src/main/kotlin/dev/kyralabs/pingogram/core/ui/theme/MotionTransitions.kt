package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Alignment

// The transitions of content that comes, goes or swaps in place (docs/DESIGN_SYSTEM.md §5 and §5.1,
// docs/plans/motion-expressive-plan.md §3.1). Spatial springs move and size it, effects springs fade it; what comes in
// fades on the default effects spring and what leaves on the fast one (Q-02), and what grew in shrinks away on the
// default spatial spring unless §6 names a fast exit (Q-01).

/**
 * A bar, card or row that takes room as it comes: its height grows from [expandFrom] on the [motionScheme]'s default
 * spatial spring while it fades in on its default effects spring.
 */
fun growInVertically(
    motionScheme: MotionScheme,
    expandFrom: Alignment.Vertical = Alignment.Top,
): EnterTransition =
    expandVertically(motionScheme.defaultSpatialSpec(), expandFrom = expandFrom) +
        fadeIn(motionScheme.defaultEffectsSpec())

/**
 * [growInVertically]'s way out: the height shrinks towards [shrinkTowards] on the [motionScheme]'s default spatial
 * spring, or its fast one where `docs/DESIGN_SYSTEM.md` §6 names a fast exit ([fast]: the sticker suggestions, the
 * floating navigation bar), while it fades out on the fast effects spring.
 */
fun shrinkOutVertically(
    motionScheme: MotionScheme,
    shrinkTowards: Alignment.Vertical = Alignment.Top,
    fast: Boolean = false,
): ExitTransition =
    shrinkVertically(
        if (fast) motionScheme.fastSpatialSpec() else motionScheme.defaultSpatialSpec(),
        shrinkTowards = shrinkTowards,
    ) + fadeOut(motionScheme.fastEffectsSpec())

/** [growInVertically] across: the width grows from [expandFrom] on the default spatial spring, with a fade in. */
fun growInHorizontally(
    motionScheme: MotionScheme,
    expandFrom: Alignment.Horizontal = Alignment.Start,
): EnterTransition =
    expandHorizontally(motionScheme.defaultSpatialSpec(), expandFrom = expandFrom) +
        fadeIn(motionScheme.defaultEffectsSpec())

/** [growInHorizontally]'s way out: the width shrinks on the default spatial spring, with a fast fade out. */
fun shrinkOutHorizontally(
    motionScheme: MotionScheme,
    shrinkTowards: Alignment.Horizontal = Alignment.Start,
): ExitTransition =
    shrinkHorizontally(motionScheme.defaultSpatialSpec(), shrinkTowards = shrinkTowards) +
        fadeOut(motionScheme.fastEffectsSpec())

/**
 * A small element that appears in place (a check, a dot, a badge): it scales up from [initialScale] on the
 * [motionScheme]'s fast spatial spring while it fades in on its fast effects spring, as material3's small components
 * move.
 */
fun popIn(
    motionScheme: MotionScheme,
    initialScale: Float = 0f,
): EnterTransition =
    scaleIn(motionScheme.fastSpatialSpec(), initialScale = initialScale) + fadeIn(motionScheme.fastEffectsSpec())

/** [popIn]'s way out: it scales down to [targetScale] on the fast spatial spring while it fades out. */
fun popOut(
    motionScheme: MotionScheme,
    targetScale: Float = 0f,
): ExitTransition =
    scaleOut(motionScheme.fastSpatialSpec(), targetScale = targetScale) + fadeOut(motionScheme.fastEffectsSpec())

/** Content that only fades: in on the [motionScheme]'s default effects spring. */
fun effectsEnter(motionScheme: MotionScheme): EnterTransition = fadeIn(motionScheme.defaultEffectsSpec())

/** [effectsEnter]'s way out: a fade on the fast effects spring. */
fun effectsExit(motionScheme: MotionScheme): ExitTransition = fadeOut(motionScheme.fastEffectsSpec())

/**
 * The `transitionSpec` of an `AnimatedContent` whose state swaps in place: the new content fades in
 * ([effectsEnter]) as the old fades out ([effectsExit]), and the container's size changes on the [motionScheme]'s
 * default spatial spring, clipped to its bounds only if [clip].
 */
fun <S> stateCrossfade(
    motionScheme: MotionScheme,
    clip: Boolean = false,
): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    {
        (effectsEnter(motionScheme) togetherWith effectsExit(motionScheme))
            .using(SizeTransform(clip = clip) { _, _ -> motionScheme.defaultSpatialSpec() })
    }
