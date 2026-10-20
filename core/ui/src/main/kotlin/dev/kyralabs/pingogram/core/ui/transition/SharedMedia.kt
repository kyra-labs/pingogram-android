package dev.kyralabs.pingogram.core.ui.transition

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp

/**
 * The [SharedTransitionScope] around the app's `NavHost` (`docs/DESIGN_SYSTEM.md` §5: container transform through
 * `SharedTransitionLayout`), for content that destinations share; null outside it (previews, a screen's own tests),
 * where nothing is shared.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The [AnimatedVisibilityScope] of the `NavHost` destination this content belongs to, which shared content follows
 * into and out of the screen; null outside a destination that provides it ([ProvideNavAnimatedVisibilityScope]).
 */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** [content] as the content of the `NavHost` destination [scope] animates, for its shared content. */
@Composable
fun ProvideNavAnimatedVisibilityScope(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}

/**
 * The container transform ([containerTransform]) of a photo, video or GIF between the conversation and the media
 * viewer (`docs/DESIGN_SYSTEM.md` §5): while the `NavHost` moves between them, this content and the other destination's
 * content of the same message [messageId] share their bounds, which grow or shrink on the motion scheme's default
 * spatial spring, the two crossfading on its default effects spring. Both are measured to the moving bounds, so a
 * photo cropped in its bubble uncrops as it grows. While they move they are drawn above both screens, clipped to a
 * shape that morphs from [counterpartShape], the other side's corners, to [shape], this side's, on the same spatial
 * spring: from a bubble's corners to the viewer's full-bleed rectangle and back.
 *
 * Each side simply comes and goes with its screen, sharing nothing, without the scopes (previews, tests, a screen
 * outside the `NavHost`), when not [enabled] (a zoomed-in photo), with reduced motion ([isReducedMotion]), and whenever
 * the other side shows no content of [messageId] (the viewer paged to media the conversation does not show, or that is
 * not loaded yet).
 */
@Composable
fun Modifier.sharedMediaBounds(
    messageId: Long,
    shape: CornerBasedShape,
    counterpartShape: CornerBasedShape,
    enabled: Boolean = true,
): Modifier = containerTransform(SharedMediaKey(messageId), shape, counterpartShape, enabled)

/**
 * A container transform between two destinations of the `NavHost` (`docs/DESIGN_SYSTEM.md` §5): while it moves between
 * them, this content and the other destination's content of the same [key] share their bounds, which grow or shrink on
 * the motion scheme's default spatial spring, the two crossfading on its default effects spring. Both are measured to
 * the moving bounds. While they move they are drawn above both screens, clipped to a shape that morphs from
 * [counterpartShape], the other side's corners, to [shape], this side's, on the same spatial spring: e.g. from a
 * floating action button's corners to a screen's square ones and back.
 *
 * Each side simply comes and goes with its screen, sharing nothing, without the scopes (previews, tests, a screen
 * outside the `NavHost`), when not [enabled], with reduced motion ([isReducedMotion]), and whenever the other side
 * shows no content of [key]. [key] must be equal on both sides and on no other content, e.g. a data object.
 */
@Composable
fun Modifier.containerTransform(
    key: Any,
    shape: CornerBasedShape,
    counterpartShape: CornerBasedShape,
    enabled: Boolean = true,
): Modifier {
    val transitionScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalNavAnimatedVisibilityScope.current
    val shares = enabled && !isReducedMotion()
    if (transitionScope == null || visibilityScope == null || !shares) return this
    val motionScheme = MaterialTheme.motionScheme
    val spatial = motionScheme.defaultSpatialSpec<Float>()
    // This side's share of the transition: 1 while it shows, 0 while the other side does; drives the corners.
    val shown =
        visibilityScope.transition.animateFloat(transitionSpec = { spatial }, label = "containerTransformShape") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
    val clip = remember(shape, counterpartShape, shown) { MorphingClip(counterpartShape, shape) { shown.value } }
    val boundsSpec = motionScheme.defaultSpatialSpec<Rect>()
    val boundsTransform = remember(boundsSpec) { BoundsTransform { _, _ -> boundsSpec } }
    return with(transitionScope) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope,
            enter = fadeIn(motionScheme.defaultEffectsSpec()),
            exit = fadeOut(motionScheme.defaultEffectsSpec()),
            boundsTransform = boundsTransform,
            resizeMode = RemeasureToBounds,
            clipInOverlayDuringTransition = clip,
        )
    }
}

/** Square corners, as full-bleed media has them in the media viewer: a [CornerBasedShape] the transform can morph. */
val FullBleedShape: CornerBasedShape = RoundedCornerShape(ZeroCornerSize)

/**
 * Whether the system asks for reduced motion: animations are off (an animator duration scale of 0, which Compose
 * applies to every animation by ending it at once). Content that moves in ways that carry no information, such as a
 * container transform, steps aside then (`docs/DESIGN_SYSTEM.md` §5).
 */
@Composable
fun isReducedMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }

/** What the two sides of a container transform of message [messageId] share. */
@Immutable
private data class SharedMediaKey(
    val messageId: Long,
)

/**
 * A rounded rectangle over the shared bounds whose corners go from those of [from] to those of [to] as [fraction]
 * goes from 0 to 1, read while the bounds move, in the draw phase. A spring's overshoot is kept within the two.
 */
private class MorphingClip(
    private val from: CornerBasedShape,
    private val to: CornerBasedShape,
    private val fraction: () -> Float,
) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val t = fraction().coerceIn(0f, 1f)
        val size = bounds.size

        fun radius(
            start: CornerSize,
            end: CornerSize,
        ) = CornerRadius(lerp(start.toPx(size, density), end.toPx(size, density), t).coerceAtLeast(0f))

        val topStart = radius(from.topStart, to.topStart)
        val topEnd = radius(from.topEnd, to.topEnd)
        val bottomEnd = radius(from.bottomEnd, to.bottomEnd)
        val bottomStart = radius(from.bottomStart, to.bottomStart)
        val ltr = layoutDirection == LayoutDirection.Ltr
        path.rewind()
        path.addRoundRect(
            RoundRect(
                rect = bounds,
                topLeft = if (ltr) topStart else topEnd,
                topRight = if (ltr) topEnd else topStart,
                bottomRight = if (ltr) bottomEnd else bottomStart,
                bottomLeft = if (ltr) bottomStart else bottomEnd,
            ),
        )
        return path
    }
}
