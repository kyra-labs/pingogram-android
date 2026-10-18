package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.launch

/**
 * The selected look of an [Avatar] (`docs/DESIGN_SYSTEM.md` §4, selected avatar): the avatar's own shape
 * ([avatarShape]: a circle for a photo, the placeholder's polygon otherwise) morphs into [SelectedAvatarPolygon] in the
 * [mark]'s container color while the avatar fades out under it, and its check grows in on top. Deselecting runs it
 * backwards.
 *
 * A modifier node rather than a composable with an effect, as for the online dot: the progress animates in the node's
 * own coroutine scope on [animationSpec] (a spatial spring) and is read only while drawing, so selecting never
 * recomposes or lays out (`docs/PERFORMANCE.md` §4). With animations turned off in the system settings, Compose ends
 * the spring at once, which skips the morph. A lazy list that reuses the node for another item shows its state at once.
 */
internal fun Modifier.avatarSelection(
    selected: Boolean,
    avatarShape: RoundedPolygon,
    mark: SelectedMark,
    animationSpec: FiniteAnimationSpec<Float>,
): Modifier = this then AvatarSelectionElement(selected, avatarShape, mark, animationSpec)

/** What a selected avatar shows: [check] in [checkColor] on its shape in [containerColor]. */
internal data class SelectedMark(
    val check: Painter,
    val containerColor: Color,
    val checkColor: Color,
)

/** The selected avatar's shape: a soft burst, the seal Google's verification badges use, here with a check. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val SelectedAvatarPolygon: RoundedPolygon get() = MaterialShapes.SoftBurst

private data class AvatarSelectionElement(
    val selected: Boolean,
    val avatarShape: RoundedPolygon,
    val mark: SelectedMark,
    val animationSpec: FiniteAnimationSpec<Float>,
) : ModifierNodeElement<AvatarSelectionNode>() {
    override fun create() = AvatarSelectionNode(selected, avatarShape, mark, animationSpec)

    override fun update(node: AvatarSelectionNode) = node.update(selected, avatarShape, mark, animationSpec)
}

private class AvatarSelectionNode(
    private var selected: Boolean,
    private var avatarShape: RoundedPolygon,
    private var mark: SelectedMark,
    private var animationSpec: FiniteAnimationSpec<Float>,
) : Modifier.Node(),
    DrawModifierNode {
    private var progress = Animatable(selected.progress)

    // Set when a lazy list reuses the node for another item, which shows its state at once rather than animating.
    private var showNextChangeAtOnce = false

    // Built when first drawn selected, for the current avatar shape.
    private var morph: Morph? = null
    private val layerPaint = Paint()
    private var cachedPath: Path? = null
    private var cachedProgress = Float.NaN
    private var cachedSize = Size.Unspecified

    fun update(
        selected: Boolean,
        avatarShape: RoundedPolygon,
        mark: SelectedMark,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        if (avatarShape != this.avatarShape) {
            this.avatarShape = avatarShape
            morph = null
            cachedPath = null
        }
        this.mark = mark
        this.animationSpec = animationSpec
        if (selected != this.selected) {
            this.selected = selected
            if (isAttached && !showNextChangeAtOnce) {
                coroutineScope.launch { progress.animateTo(selected.progress, animationSpec) }
            } else {
                // A plain field: the update invalidates drawing, which reads the new animatable.
                progress = Animatable(selected.progress)
            }
        }
    }

    override fun onReset() {
        showNextChangeAtOnce = true
    }

    // Detaching cancels a running animation; start from the end state when attached again.
    override fun onDetach() {
        progress = Animatable(selected.progress)
    }

    override fun ContentDrawScope.draw() {
        showNextChangeAtOnce = false
        // The spatial spring overshoots: past 1 the check grows a little further, the rest stays at its end.
        val value = progress.value
        val shown = value.coerceIn(0f, 1f)
        if (shown == 0f) {
            drawContent()
            return
        }
        if (shown < 1f) {
            // The avatar fades out under the growing selection, so its edges do not show between the burst's points.
            layerPaint.alpha = 1f - shown
            drawContext.canvas.saveLayer(size.toRect(), layerPaint)
            drawContent()
            drawContext.canvas.restore()
        }
        drawPath(selectionPath(shown), mark.containerColor, alpha = shown)
        val iconSize = size.minDimension * CHECK_SIZE_RATIO * value.coerceAtLeast(0f)
        translate(left = (size.width - iconSize) / 2, top = (size.height - iconSize) / 2) {
            with(
                mark.check,
            ) { draw(Size(iconSize, iconSize), alpha = shown, colorFilter = ColorFilter.tint(mark.checkColor)) }
        }
    }

    /** The morph from the avatar's shape to the selected one at [shown], filling the avatar's bounds, centered. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun ContentDrawScope.selectionPath(shown: Float): Path {
        cachedPath?.let { if (shown == cachedProgress && size == cachedSize) return it }
        val morph = morph ?: Morph(avatarShape, SelectedAvatarPolygon).also { morph = it }
        // A new path per frame: outlines and draw calls compare paths by identity.
        val path = morph.toPath(shown, Path())
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        cachedPath = path
        cachedProgress = shown
        cachedSize = size
        return path
    }
}

private val Boolean.progress get() = if (this) 1f else 0f

// The check takes about half of the avatar, inside the burst's points.
private const val CHECK_SIZE_RATIO = 0.5f
