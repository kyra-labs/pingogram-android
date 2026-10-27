package dev.kyralabs.pingogram.core.ui.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
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

    // How many of the viewer's pages show each message's media and share it with its bubble; snapshot state, which
    // each bubble reads through a derived state of its own message.
    private val inViewer = mutableStateMapOf<Long, Int>()

    /**
     * Whether the media viewer shows the media of message [messageId] and shares it with its bubble
     * ([sharedMediaViewerBounds]): the bubble's media is then the side the viewer's grew out of, and shrinks back
     * into once the viewer leaves it ([sharedMediaBounds]).
     */
    fun isInViewer(messageId: Long): Boolean = (inViewer[messageId] ?: 0) > 0

    internal fun enterViewer(messageId: Long) {
        inViewer[messageId] = (inViewer[messageId] ?: 0) + 1
    }

    internal fun leaveViewer(messageId: Long) {
        val count = (inViewer[messageId] ?: 0) - 1
        if (count > 0) inViewer[messageId] = count else inViewer.remove(messageId)
    }

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
 * conversation and the media viewer (`docs/DESIGN_SYSTEM.md` §5): while the viewer, an overlay destination above the
 * conversation (`OverlayNavigator`), comes and goes, this content and the viewer's content of the same message
 * [messageId] share their bounds, which grow or shrink on the motion
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
    val shapes = LocalSharedMediaShapes.current ?: return this
    // The viewer is an overlay above the conversation, which stays on screen (core:ui's OverlayNavigator): the media
    // goes with the viewer, not with the conversation's own destination.
    val viewer = rememberViewerVisibility(shapes, messageId)
    val shared =
        containerTransformOf(SharedMediaKey(messageId), shape, FullBleedCounterpart, enabled, visibilityScope = viewer)
    return if (shared !== this) {
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
    val shared = containerTransformOf(SharedMediaKey(messageId), FullBleedShape, counterpart, enabled)
    // While shared and coming in or shown, the bubble's media is the side going out; leaving, it comes back.
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (shared !== this && shapes != null && visibility?.transition?.targetState == EnterExitState.Visible) {
        DisposableEffect(shapes, messageId) {
            shapes.enterViewer(messageId)
            onDispose { shapes.leaveViewer(messageId) }
        }
    }
    return shared
}

/**
 * The bubble's side of the media viewer's coming and going, for its media of message [messageId]: gone while the
 * viewer shows that media ([SharedMediaShapes.isInViewer]), back as it leaves it. Only the bubbles whose media the
 * viewer takes or gives back compose again.
 */
@Composable
private fun rememberViewerVisibility(
    shapes: SharedMediaShapes,
    messageId: Long,
): AnimatedVisibilityScope {
    val inViewer by remember(shapes, messageId) { derivedStateOf { shapes.isInViewer(messageId) } }
    val transition =
        updateTransition(if (inViewer) EnterExitState.PostExit else EnterExitState.Visible, label = "sharedMedia")
    return remember(transition) { ViewerVisibility(transition) }
}

/** An [AnimatedVisibilityScope] of [transition], which content stays composed through. */
private class ViewerVisibility(
    override val transition: Transition<EnterExitState>,
) : AnimatedVisibilityScope

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
 *
 * With [fixedLayout], this side is laid out at the size it ends at (from the transform's lookahead) and revealed by
 * the moving bounds from their top start, so its layout is measured once and every frame of the transform only clips
 * it; without, it is measured anew at every frame's bounds. A whole screen (New chat, search, a profile) keeps its
 * layout, as measuring its lists, bars and text anew in every frame made the transform stutter; small content (a
 * button, a photo) is measured anew.
 */
@Composable
fun Modifier.containerTransform(
    key: Any,
    shape: CornerBasedShape,
    counterpartShape: CornerBasedShape,
    enabled: Boolean = true,
    fixedLayout: Boolean = false,
): Modifier =
    containerTransformOf(key, shape, remember(counterpartShape) { { counterpartShape } }, enabled, fixedLayout)

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
    fixedLayout: Boolean = false,
    visibilityScope: AnimatedVisibilityScope? = LocalNavAnimatedVisibilityScope.current,
): Modifier {
    val transitionScope = LocalSharedTransitionScope.current
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
    val target = remember { TargetConstraints() }
    val shared =
        with(transitionScope) {
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
    return if (fixedLayout) shared.measuredAtTarget(target) else shared
}

/** The constraints content gets in the lookahead pass: those it ends the transform with. Read in the layout phase. */
private class TargetConstraints {
    var value: Constraints? = null
}

/**
 * Measures the content with the constraints of the lookahead pass ([target]) whatever the moving bounds give it, and
 * places it at their top start: as the constraints stay the same in every frame, Compose keeps its measurement, and
 * only this layout runs again.
 */
private fun Modifier.measuredAtTarget(target: TargetConstraints): Modifier =
    layout { measurable, constraints ->
        if (isLookingAhead) target.value = constraints
        val placeable = measurable.measure(target.value ?: constraints)
        layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height)) {
            placeable.place(0, 0)
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
