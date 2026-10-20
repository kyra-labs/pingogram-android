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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp

/**
 * The [SharedTransitionScope] around the app's `NavHost` (`docs/DESIGN_SYSTEM.md` §5: container transform through
 * `SharedTransitionLayout`), for content that destinations share; null outside it (previews, a screen's own tests),
 * where nothing is shared. Provided with [ProvideSharedTransitionScope].
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The [SharedMediaShapes] of the [SharedTransitionScope] around the app's `NavHost` ([ProvideSharedTransitionScope]);
 * null outside it, where nothing is shared.
 */
val LocalSharedMediaShapes = staticCompositionLocalOf<SharedMediaShapes?> { null }

/**
 * The [AnimatedVisibilityScope] of the `NavHost` destination this content belongs to, which shared content follows
 * into and out of the screen; null outside a destination that provides it ([ProvideNavAnimatedVisibilityScope]).
 */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * [content] in [scope], the [SharedTransitionScope] around the app's `NavHost` ([LocalSharedTransitionScope]), with
 * the [SharedMediaShapes] its container transforms share ([LocalSharedMediaShapes]).
 */
@Composable
fun ProvideSharedTransitionScope(
    scope: SharedTransitionScope,
    content: @Composable () -> Unit,
) {
    val shapes = remember { SharedMediaShapes() }
    CompositionLocalProvider(
        LocalSharedTransitionScope provides scope,
        LocalSharedMediaShapes provides shapes,
        content = content,
    )
}

/** [content] as the content of the `NavHost` destination [scope] animates, for its shared content. */
@Composable
fun ProvideNavAnimatedVisibilityScope(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}

/**
 * The corners the media of each message shows in the conversation, where the media viewer's side of the container
 * transform starts and ends (`docs/DESIGN_SYSTEM.md` §4: the media's clip in its bubble). The media of a bubble
 * publishes its clip while it is in the layout ([sharedMediaBounds]), and the viewer reads it in the draw phase, while
 * the bounds move ([sharedMediaViewerBounds]): only the conversation knows a bubble's corners (its place in the group,
 * a header, a caption), and they can change while the viewer is open (a newer message joins the group). The shapes
 * are immutable; what is published changes only as bubbles enter and leave the layout. Main thread only.
 */
@Stable
class SharedMediaShapes {
    private val published = HashMap<Long, PublishedMediaShapeNode>()

    /** The corners the media of message [messageId] shows in its bubble; null while no bubble of it is laid out. */
    operator fun get(messageId: Long): CornerBasedShape? = published[messageId]?.shape

    internal fun publish(node: PublishedMediaShapeNode) {
        published[node.messageId] = node
    }

    /** Withdraws [node]'s shape, unless another bubble of its message has published one since. */
    internal fun withdraw(node: PublishedMediaShapeNode) {
        if (published[node.messageId] === node) published.remove(node.messageId)
    }
}

/**
 * The conversation's side of the container transform ([containerTransform]) of a photo, video or GIF between the
 * conversation and the media viewer (`docs/DESIGN_SYSTEM.md` §5): while the `NavHost` moves between them, this content
 * and the viewer's content of the same message [messageId] share their bounds, which grow or shrink on the motion
 * scheme's default spatial spring, the two crossfading on its default effects spring. Both are measured to the moving
 * bounds, so a photo cropped in its bubble uncrops as it grows. While they move they are drawn above both screens,
 * each clipped to a shape that morphs between [shape], the media's clip in its bubble (the bubble's corners, square
 * where a header or a caption joins the media), and the viewer's square corners, on the same spatial spring. The
 * viewer's side morphs between the same two ([sharedMediaViewerBounds]): [shape] is published for it in
 * [LocalSharedMediaShapes], so both sides end on exactly the bubble's corners, and nothing snaps when the transform
 * ends.
 *
 * Each side simply comes and goes with its screen, sharing nothing, without the scopes (previews, tests, a screen
 * outside the `NavHost`), when not [enabled] (media the viewer does not show), with reduced motion
 * ([isReducedMotion]), and whenever the other side shows no content of [messageId] (the viewer paged to media the
 * conversation does not show, or that is not loaded yet).
 */
@Composable
fun Modifier.sharedMediaBounds(
    messageId: Long,
    shape: CornerBasedShape,
    enabled: Boolean = true,
): Modifier {
    val shapes = LocalSharedMediaShapes.current
    val shared = containerTransformOf(SharedMediaKey(messageId), shape, FullBleedCounterpart, enabled)
    return if (shared !== this && shapes != null) {
        shared.then(PublishedMediaShapeElement(shapes, messageId, shape))
    } else {
        shared
    }
}

/**
 * The media viewer's side of the container transform of [sharedMediaBounds]: full-bleed media of message [messageId],
 * with square corners, which morph from and back to the corners the media shows in its bubble, as the conversation
 * publishes them ([SharedMediaShapes]), so the way back ends on exactly those. Nothing is shared when not [enabled] (a
 * zoomed-in photo, which fades out with the screen instead).
 */
@Composable
fun Modifier.sharedMediaViewerBounds(
    messageId: Long,
    enabled: Boolean = true,
): Modifier {
    val shapes = LocalSharedMediaShapes.current
    val counterpart = remember(shapes, messageId) { viewerCounterpart(shapes, messageId) }
    return containerTransformOf(SharedMediaKey(messageId), FullBleedShape, counterpart, enabled)
}

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
): Modifier = containerTransformOf(key, shape, remember(counterpartShape) { { counterpartShape } }, enabled)

/**
 * [containerTransform] with the other side's corners read while the bounds move, in the draw phase ([counterpart]).
 * Returns this modifier itself when nothing is shared.
 */
@Composable
private fun Modifier.containerTransformOf(
    key: Any,
    shape: CornerBasedShape,
    counterpart: () -> CornerBasedShape,
    enabled: Boolean,
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
    val clip = remember(shape, counterpart, shown) { MorphingClip(counterpart, shape) { shown.value } }
    val boundsSpec = motionScheme.defaultSpatialSpec<Rect>()
    val boundsTransform = remember(boundsSpec) { BoundsTransform { _, _ -> boundsSpec } }
    return with(transitionScope) {
        this@containerTransformOf.sharedBounds(
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

/** The viewer's square corners, which media in a bubble morphs to and from. */
private val FullBleedCounterpart: () -> CornerBasedShape = { FullBleedShape }

/**
 * The corners the viewer's media of message [messageId] morphs from and back to: those its bubble publishes in
 * [shapes], read whenever the clip is drawn; square while there are none.
 */
internal fun viewerCounterpart(
    shapes: SharedMediaShapes?,
    messageId: Long,
): () -> CornerBasedShape = { shapes?.get(messageId) ?: FullBleedShape }

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

/** Publishes [shape] in [shapes] as the corners of message [messageId]'s media, while it is in the layout. */
private data class PublishedMediaShapeElement(
    val shapes: SharedMediaShapes,
    val messageId: Long,
    val shape: CornerBasedShape,
) : ModifierNodeElement<PublishedMediaShapeNode>() {
    override fun create() = PublishedMediaShapeNode(shapes, messageId, shape)

    override fun update(node: PublishedMediaShapeNode) = node.update(shapes, messageId, shape)

    override fun InspectorInfo.inspectableProperties() {
        name = "publishedMediaShape"
        properties["messageId"] = messageId
        properties["shape"] = shape
    }
}

/**
 * [shape] in [shapes] for message [messageId], from the moment the node is attached until it is detached: no
 * composition effect per bubble, and nothing kept for bubbles that left.
 */
internal class PublishedMediaShapeNode(
    private var shapes: SharedMediaShapes,
    var messageId: Long,
    var shape: CornerBasedShape,
) : Modifier.Node() {
    override fun onAttach() = shapes.publish(this)

    override fun onDetach() = shapes.withdraw(this)

    fun update(
        shapes: SharedMediaShapes,
        messageId: Long,
        shape: CornerBasedShape,
    ) {
        if (isAttached) this.shapes.withdraw(this)
        this.shapes = shapes
        this.messageId = messageId
        this.shape = shape
        if (isAttached) shapes.publish(this)
    }
}

/**
 * A rounded rectangle over the shared bounds whose corners go from those of [from] to those of [to] as [fraction] goes
 * from 0 to 1, both read while the bounds move, in the draw phase. A spring's overshoot is kept within the two.
 */
internal class MorphingClip(
    private val from: () -> CornerBasedShape,
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
        val from = from()

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
