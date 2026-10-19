package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.sharedMediaBounds
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The photos and videos of an album (`REQUIREMENTS.md` FR-C4: consecutive messages with the same `mediaAlbumId`, which
 * the screen passes as [items]) as one mosaic, edge to edge in the bubble ([isEdgeToEdge]) and clipped to [shape].
 *
 * - **Layout**: one custom [Layout] ([albumLayout]): rows [maxWidth] wide whose items keep their aspect ratios, chosen
 *   so the album comes out about square, `mediaAlbumGap` apart. In a narrower bubble the mosaic scales down.
 * - **Items**: photos as in [MessagePhoto], videos and GIFs as their thumbnail with their length or "GIF"; each in
 *   `extraSmall` corners inside the album's [shape], so only the album's outer corners are round. Spoilers show only
 *   their blurred minithumbnail. Other content, which albums never hold, shows as a tonal cell.
 * - Tapping an item calls [onOpen] with its index in [items]. Telegram albums hold at most [MAX_ALBUM_ITEMS]; more are
 *   left out.
 * - Each item grows into the media viewer and shrinks back into its cell ([sharedMediaBounds]) by its own message, of
 *   [messageIds] (in the order of [items]; none in previews), from and to its `extraSmall` corners.
 * - An item being sent shows its upload ([uploads], by message id) with a cancel button ([onCancelUpload]).
 *
 * The caption, if any, is the bubble's: Telegram shows the caption of the album's one captioned item below it.
 */
@Composable
internal fun MediaAlbum(
    items: ImmutableList<MessageContent>,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
    messageIds: ImmutableList<Long> = persistentListOf(),
    uploads: ImmutableMap<Long, MessageUpload> = persistentMapOf(),
    onCancelUpload: (messageId: Long) -> Unit = {},
) {
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.roundToPx() }
    val gapPx = with(density) { MaterialTheme.sizes.mediaAlbumGap.roundToPx() }
    val shown = if (items.size > MAX_ALBUM_ITEMS) items.subList(0, MAX_ALBUM_ITEMS) else items
    val layout = remember(shown, widthPx, gapPx) { albumLayout(shown.map(::albumAspectRatio), widthPx, gapPx) }
    val cellShape = MaterialTheme.shapes.extraSmall
    val openLabel = stringResource(R.string.content_open)
    Layout(
        content = {
            shown.forEachIndexed { index, item ->
                val cell = layout.cells[index]
                val cellSize = with(density) { DpSize(cell.width.toDp(), cell.height.toDp()) }
                val messageId = messageIds.getOrNull(index)
                Box(
                    Modifier
                        .sharedMediaBounds(
                            messageId = messageId ?: 0L,
                            shape = cellShape,
                            counterpartShape = FullBleedShape,
                            enabled = messageId != null && item.opensInViewer,
                        ).clip(cellShape)
                        .clickable(onClickLabel = openLabel) { onOpen(index) },
                ) {
                    AlbumItem(item = item, displaySize = cellSize, modifier = Modifier.matchParentSize())
                    messageId?.let(uploads::get)?.let { upload ->
                        MediaUploadProgress(
                            upload = upload,
                            onCancel = { onCancelUpload(messageId) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        },
        modifier = modifier.clip(shape),
        measurePolicy = remember(layout) { AlbumMeasurePolicy(layout) },
    )
}

/** Places each item at its cell of [album], scaled down if the constraints are narrower than the album. */
private class AlbumMeasurePolicy(
    private val album: AlbumLayout,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val scale =
            if (constraints.hasBoundedWidth && constraints.maxWidth < album.width) {
                constraints.maxWidth.toFloat() / album.width
            } else {
                1f
            }
        val cells = album.cells.map { it.scaled(scale) }
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val cell = cells[index]
                measurable.measure(Constraints.fixed(cell.width.coerceAtLeast(0), cell.height.coerceAtLeast(0)))
            }
        return layout((album.width * scale).roundToInt(), (album.height * scale).roundToInt()) {
            placeables.forEachIndexed { index, placeable -> placeable.place(cells[index].left, cells[index].top) }
        }
    }
}

private fun IntRect.scaled(scale: Float): IntRect =
    if (scale == 1f) {
        this
    } else {
        IntRect(
            (left * scale).roundToInt(),
            (top * scale).roundToInt(),
            (right * scale).roundToInt(),
            (bottom * scale).roundToInt(),
        )
    }

/** Width / height of an album item, from its largest photo size or the video's size; 1 when unknown. */
private fun albumAspectRatio(item: MessageContent): Float {
    val (width, height) =
        when (item) {
            is MessageContent.Photo -> largestPhotoSize(item.photo.sizes).let { (it?.width ?: 0) to (it?.height ?: 0) }
            is MessageContent.Video -> item.video.width to item.video.height
            is MessageContent.Animation -> item.animation.width to item.animation.height
            else -> 0 to 0
        }
    return if (width > 0 && height > 0) width.toFloat() / height else 1f
}

@Composable
private fun AlbumItem(
    item: MessageContent,
    displaySize: DpSize,
    modifier: Modifier,
) {
    when (item) {
        is MessageContent.Photo -> {
            val description = stringResource(UiR.string.preview_photo)
            PhotoImage(
                photo = item.photo,
                displaySize = displaySize,
                hidden = item.hasSpoiler || item.isSecret,
                modifier = modifier.semantics { contentDescription = description },
            )
        }

        is MessageContent.Video -> {
            AlbumVideo(
                still = item.video.still(),
                hidden = item.hasSpoiler || item.isSecret,
                displaySize = displaySize,
                kind = stringResource(UiR.string.preview_video),
                label = mediaDurationText(item.video.duration),
                modifier = modifier,
            )
        }

        is MessageContent.Animation -> {
            val gif = stringResource(UiR.string.preview_gif)
            AlbumVideo(
                still = item.animation.still(),
                hidden = item.hasSpoiler || item.isSecret,
                displaySize = displaySize,
                kind = gif,
                label = gif,
                modifier = modifier,
            )
        }

        else -> {
            Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

/** A video or GIF in an album: its [still] ([VideoThumbnail]) with [label] (its length, or "GIF") at the top start. */
@Composable
private fun AlbumVideo(
    still: VideoStill,
    hidden: Boolean,
    displaySize: DpSize,
    kind: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val description = if (kind == label) kind else stringResource(R.string.content_kind_and_details, kind, label)
    Box(modifier.semantics { contentDescription = description }) {
        VideoThumbnail(still, displaySize, hidden, Modifier.matchParentSize())
        MediaLabel(
            text = label,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(MaterialTheme.spacing.extraSmall)
                    .clearAndSetSemantics {},
        )
    }
}
