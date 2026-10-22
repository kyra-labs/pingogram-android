package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendOptionsMenu
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.MinithumbnailPlaceholder
import dev.kyralabs.pingogram.feature.chat.ui.content.StickerImage
import dev.kyralabs.pingogram.feature.chat.ui.content.TdlibImage
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.feature.chat.ui.content.fadingOutAs
import dev.kyralabs.pingogram.feature.chat.ui.content.fitInSquare
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberImageAlpha
import dev.kyralabs.pingogram.feature.chat.ui.content.selectPhotoSize
import dev.kyralabs.pingogram.core.ui.R as UiR

// The results of an inline bot above the composer (InlineResultsArea): photos, GIFs, stickers and videos as cells of a
// grid, everything else as rows across it. Each is one item for screen readers, its kind and title.

/**
 * A photo, GIF, sticker or video result as a square cell of [cellSize] (decoded at [cellPx]), `extraSmall` corners as
 * the shared media's cells, dimmed in dark theme as the conversation's media; [highlighted] while a hardware keyboard
 * picked it (`secondaryContainer` behind it, `large` corners).
 */
@Composable
internal fun InlineResultCell(
    entry: InlineResultEntry,
    cellSize: Dp,
    cellPx: Int,
    highlighted: Boolean,
    actions: InlineSendActions,
    modifier: Modifier = Modifier,
) {
    val colorFilter = MaterialTheme.pingogramColors.mediaColorFilter
    val shape = if (highlighted) MaterialTheme.shapes.large else MaterialTheme.shapes.extraSmall
    InlineResultTarget(entry, actions, modifier.aspectRatio(1f).clip(shape)) {
        val highlight = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        val frame = if (highlighted) MaterialTheme.spacing.extraSmall else Dp.Hairline
        val label = Modifier.padding(MaterialTheme.spacing.extraSmall)
        Box(Modifier.background(highlight).padding(frame)) {
            when (val result = entry.result) {
                is InlineResult.Photo -> {
                    val photo = result.photo
                    val photoSize = remember(photo, cellPx) { selectPhotoSize(photo.sizes, cellPx, cellPx) }
                    CellImage(photoSize?.file, photo.minithumbnail, cellSize, cellPx, colorFilter)
                }

                is InlineResult.Animation -> {
                    val animation = result.animation
                    CellImage(animation.thumbnail.pictureFile(), animation.minithumbnail, cellSize, cellPx, colorFilter)
                    MediaLabel(stringResource(UiR.string.preview_gif), label.align(Alignment.BottomStart))
                }

                is InlineResult.Video -> {
                    val video = result.video
                    CellImage(video.thumbnail.pictureFile(), video.minithumbnail, cellSize, cellPx, colorFilter)
                    MediaLabel(mediaDurationText(video.duration), label.align(Alignment.BottomStart))
                }

                is InlineResult.Sticker -> {
                    StickerCell(result, cellSize)
                }

                else -> {}
            }
        }
    }
}

/** A cell's picture: [file] decoded at [cellPx] over the blurred [minithumbnail], which fades out as it fades in. */
@Composable
private fun CellImage(
    file: FileRef?,
    minithumbnail: Minithumbnail?,
    cellSize: Dp,
    cellPx: Int,
    colorFilter: ColorFilter?,
) {
    val alpha = rememberImageAlpha(file?.id ?: 0)
    Box(Modifier.aspectRatio(1f)) {
        MinithumbnailPlaceholder(
            minithumbnail,
            DpSize(cellSize, cellSize),
            Modifier.matchParentSize().fadingOutAs(alpha),
            colorFilter,
        )
        if (file != null) {
            TdlibImage(file.id, cellPx, cellPx, alpha, Modifier.matchParentSize(), colorFilter = colorFilter)
        }
    }
}

/** A sticker fitted into its cell less the sticker panel's padding, centred, as in the panel. */
@Composable
private fun StickerCell(
    result: InlineResult.Sticker,
    cellSize: Dp,
) {
    val sticker = result.sticker
    val side = cellSize - MaterialTheme.spacing.small * 2
    val display = remember(sticker.width, sticker.height, side) { fitInSquare(sticker.width, sticker.height, side) }
    Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) { StickerImage(sticker, display) }
}

/**
 * An article, contact, location, venue, song, file, voice message or game as a row across the grid: Material's list
 * item without a container of its own (the area's), its picture or kind's icon at the start in a `secondaryContainer`
 * circle as the shared media's file rows, the title in `bodyLarge` over what the bot says of it in `bodyMedium`, both
 * on one line, as user content; [highlighted] (`secondaryContainer`, `large`) while a hardware keyboard picked it.
 */
@Composable
internal fun InlineResultRow(
    entry: InlineResultEntry,
    highlighted: Boolean,
    actions: InlineSendActions,
    modifier: Modifier = Modifier,
) {
    val texts = rowTexts(entry.result)
    val colors = MaterialTheme.colorScheme
    InlineResultTarget(entry, actions, modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)) {
        ListItem(
            supportingContent =
                texts.supporting.takeIf { it.isNotBlank() }?.let { supporting ->
                    {
                        Text(
                            text = supporting,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                if (texts.isNumeric) {
                                    MaterialTheme.typography.bodyMedium.numericContent
                                } else {
                                    MaterialTheme.typography.bodyMedium.userContent
                                },
                        )
                    }
                },
            leadingContent = { RowPicture(entry.result) },
            colors =
                ListItemDefaults.colors(
                    containerColor = if (highlighted) colors.secondaryContainer else Color.Transparent,
                ),
        ) {
            Text(
                text = texts.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.userContent,
            )
        }
    }
}

/** A row's picture: the bot's thumbnail, rounded and dimmed in dark theme; without one, its kind's icon in a circle. */
@Composable
private fun RowPicture(result: InlineResult) {
    val side = MaterialTheme.sizes.contentIcon
    val thumbnail = result.rowThumbnail()?.asPicture()
    if (thumbnail != null) {
        val sidePx = with(LocalDensity.current) { side.roundToPx() }
        val alpha = rememberImageAlpha(thumbnail.file.id)
        Box(
            Modifier
                .size(side)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            TdlibImage(
                thumbnail.file.id,
                sidePx,
                sidePx,
                alpha,
                Modifier.matchParentSize(),
                colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
            )
        }
    } else {
        Box(
            Modifier.size(side).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(result.kindIcon()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The file of this thumbnail where Coil can show it as a picture. */
private fun Thumbnail?.pictureFile(): FileRef? = this?.asPicture()?.file
