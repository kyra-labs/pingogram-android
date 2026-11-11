package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.StickerInk
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The sticker being made, a square [side] wide: [photo] filling it as [MakeStickerUiState.crop] says, clipped to
 * rounded corners ([StickerMakerLimits.CORNER_FRACTION] of the side), the outline around it while
 * [MakeStickerUiState.outline], and the layers on top. Everything it draws is recorded into [capture], which "Next"
 * turns into the sticker; what is around it (the checkered ground, which stands for transparency) is not.
 *
 * Dragging the photo moves it, pinching zooms it, both held while the fingers are down (read in the draw phase only)
 * and sent once they lift ([MakeStickerEvent.Cropped]); a tap where no layer is touches none. Each layer drags on its
 * own ([StickerLayerItem]). Screen readers hear the photo, and zoom it through its custom actions.
 */
@Composable
internal fun StickerCanvas(
    state: MakeStickerUiState,
    photo: Painter,
    capture: GraphicsLayer?,
    onEvent: (MakeStickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    var crop by remember(state.crop) { mutableStateOf(state.crop) }
    val photoDescription = stringResource(R.string.make_sticker_photo)
    val zoomIn = stringResource(R.string.make_sticker_zoom_in)
    val zoomOut = stringResource(R.string.make_sticker_zoom_out)
    BoxWithConstraints(modifier) {
        val side = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .then(if (capture != null) Modifier.recordInto(capture) else Modifier)
                .pointerInput(photo) {
                    cropGestures(
                        photoAspect = photo.aspect(),
                        crop = { crop },
                        onCrop = { crop = it },
                        onEnd = { changed ->
                            currentOnEvent(
                                if (changed) MakeStickerEvent.Cropped(crop) else MakeStickerEvent.Select(null),
                            )
                        },
                    )
                }.drawPhoto(photo, { crop }, state.outline)
                .semantics {
                    contentDescription = photoDescription
                    customActions =
                        listOf(
                            CustomAccessibilityAction(zoomIn) {
                                currentOnEvent(MakeStickerEvent.Cropped(crop.copy(zoom = crop.zoom * ZOOM_STEP)))
                                true
                            },
                            CustomAccessibilityAction(zoomOut) {
                                currentOnEvent(MakeStickerEvent.Cropped(crop.copy(zoom = crop.zoom / ZOOM_STEP)))
                                true
                            },
                        )
                },
        ) {
            state.layers.forEach { layer ->
                key(layer.id) {
                    StickerLayerItem(layer, side, selected = layer.id == state.selected, onEvent = onEvent)
                }
            }
        }
    }
}

/** Records what this draws into [layer] and draws the layer: "Next" reads it back as the sticker. */
private fun Modifier.recordInto(layer: GraphicsLayer): Modifier =
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

/**
 * Draws [photo] as [crop] says, filling the square and clipped to its rounded corners, then the white outline where
 * [outline]; [crop] is read in the draw phase, so dragging recomposes nothing.
 */
private fun Modifier.drawPhoto(
    photo: Painter,
    crop: () -> StickerCrop,
    outline: Boolean,
): Modifier =
    drawWithCache {
        val radius = size.minDimension * StickerMakerLimits.CORNER_FRACTION
        val clip =
            Path().apply {
                addRoundRect(
                    RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)),
                )
            }
        val stroke = size.minDimension * StickerMakerLimits.OUTLINE_FRACTION
        onDrawWithContent {
            val shown = crop()
            val intrinsic = photo.intrinsicSize
            val photoSize = if (intrinsic.isSpecified()) intrinsic else size
            val scale = max(size.width / photoSize.width, size.height / photoSize.height) * shown.zoom
            val drawn = Size(photoSize.width * scale, photoSize.height * scale)
            clipPath(clip) {
                translate(
                    left = (size.width - drawn.width) / 2 + shown.panX * size.width,
                    top = (size.height - drawn.height) / 2 + shown.panY * size.height,
                ) { with(photo) { draw(drawn) } }
            }
            if (outline) {
                drawRoundRect(
                    color = StickerInk.Outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius - stroke / 2),
                    style = Stroke(width = stroke),
                )
            }
            drawContent()
        }
    }

/** Whether this size is known: a picture's, not a brush's. */
private fun Size.isSpecified() = this != Size.Unspecified && width > 0f && height > 0f

/** How many times wider than tall the painter's picture is; 1 while that is unknown. */
private fun Painter.aspect(): Float = intrinsicSize.takeIf { it.isSpecified() }?.let { it.width / it.height } ?: 1f

/**
 * Moves and zooms the photo while the fingers are down: [onCrop] with each change, kept within the photo's edges
 * ([photoAspect]); [onEnd] once they lift, whether anything changed. A gesture a layer took (consumed) does nothing.
 */
private suspend fun PointerInputScope.cropGestures(
    photoAspect: Float,
    crop: () -> StickerCrop,
    onCrop: (StickerCrop) -> Unit,
    onEnd: (changed: Boolean) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var changed = false
        var taken = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) {
                taken = true
                break
            }
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (zoom != 1f || pan != Offset.Zero) {
                val current = crop()
                onCrop(
                    current
                        .copy(
                            zoom = current.zoom * zoom,
                            panX = current.panX + pan.x / size.width,
                            panY = current.panY + pan.y / size.height,
                        ).within(photoAspect),
                )
                event.changes.forEach { it.consume() }
                changed = true
            }
        } while (event.changes.any { it.pressed })
        if (!taken) onEnd(changed)
    }
}

/** [this] zoomed between 1 and [StickerMakerLimits.ZOOM_MAX] times, moved no further than the photo's edges. */
internal fun StickerCrop.within(photoAspect: Float): StickerCrop {
    val shown = coerced()
    val roomX = (shown.zoom * max(1f, photoAspect) - 1f) / 2f
    val roomY = (shown.zoom * max(1f, 1f / photoAspect) - 1f) / 2f
    return shown.copy(panX = shown.panX.coerceIn(-roomX, roomX), panY = shown.panY.coerceIn(-roomY, roomY))
}

/**
 * Layer [layer] drawn on the square of [side] pixels, centred at its place: text in `displaySmallEmphasized`, white
 * with a near-black edge ([StickerInk]), or an emoji in `displayLarge`. Dragging moves it, held while the finger is
 * down (read in the layout phase) and sent once it lifts ([MakeStickerEvent.Moved]); a tap touches it ([selected]
 * draws a `primary` frame, left out of the sticker, as "Next" touches none). Screen readers hear its kind and content,
 * and remove it through its custom action.
 */
@Composable
private fun StickerLayerItem(
    layer: StickerLayer,
    side: Float,
    selected: Boolean,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    var place by remember(layer.x, layer.y) { mutableStateOf(Offset(layer.x, layer.y)) }
    val description =
        stringResource(
            if (layer.kind == LayerKind.Text) R.string.make_sticker_layer_text else R.string.make_sticker_layer_emoji,
            layer.content,
        )
    val remove = stringResource(R.string.make_sticker_remove)
    Box(
        Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(0, 0) {
                    placeable.place(
                        (place.x * side - placeable.width / 2f).roundToInt(),
                        (place.y * side - placeable.height / 2f).roundToInt(),
                    )
                }
            }.pointerInput(layer.id, side) {
                detectDragGestures(
                    onDragStart = { currentOnEvent(MakeStickerEvent.Select(layer.id)) },
                    onDragEnd = { currentOnEvent(MakeStickerEvent.Moved(layer.id, place.x, place.y)) },
                ) { change, drag ->
                    change.consume()
                    place =
                        Offset((place.x + drag.x / side).coerceIn(0f, 1f), (place.y + drag.y / side).coerceIn(0f, 1f))
                }
            }.pointerInput(layer.id) { detectTapGestures { currentOnEvent(MakeStickerEvent.Select(layer.id)) } }
            .then(
                if (selected) {
                    Modifier.border(
                        MaterialTheme.sizes.avatarRing,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.small,
                    )
                } else {
                    Modifier
                },
            ).semantics {
                contentDescription = description
                customActions =
                    listOf(
                        CustomAccessibilityAction(remove) {
                            currentOnEvent(MakeStickerEvent.Remove(layer.id))
                            true
                        },
                    )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (layer.kind) {
            LayerKind.Text -> StickerText(layer.content)
            LayerKind.Emoji -> Text(layer.content, style = MaterialTheme.typography.displayLarge)
        }
    }
}

/** [text] as a sticker's: white over a near-black edge drawn behind it, so it reads on any photo. */
@Composable
private fun StickerText(text: String) {
    val style = MaterialTheme.typography.displaySmallEmphasized.userContent
    val edge = with(LocalDensity.current) { MaterialTheme.spacing.extraSmall.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = StickerInk.TextEdge,
            textAlign = TextAlign.Center,
            style = style.copy(drawStyle = Stroke(width = edge, join = StrokeJoin.Round)),
        )
        Text(text = text, color = StickerInk.Text, textAlign = TextAlign.Center, style = style)
    }
}

/** How much one zoom action of screen readers zooms in or out. */
private const val ZOOM_STEP = 1.25f
