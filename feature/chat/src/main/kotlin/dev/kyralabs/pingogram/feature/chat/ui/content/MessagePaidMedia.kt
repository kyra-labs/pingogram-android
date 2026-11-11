package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.PaidMediaItem
import dev.kyralabs.pingogram.core.domain.model.mediaContents
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.ui.image.BlurredMinithumbnail
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.MediaDownloads
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.giveawayNumber
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.toPluralCount
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A paid post (`REQUIREMENTS.md` FR-C4, the owner's board 2; `docs/DESIGN_SYSTEM.md` §6 "Paid media"), edge to edge in
 * its bubble like an album, clipped to [shape]:
 *
 * - **Not paid for**: the previews' blurred minithumbnails in the album's mosaic ([albumLayout]) under a scrim, with
 *   the price on a gold pill (`tertiaryContainer`, a lock and the star symbol) and how many photos and videos there
 *   are; nothing is tappable, as Pingogram offers no purchase (`REQUIREMENTS.md` §1.2), and a note below the mosaic
 *   says so and where to unlock the post. Screen readers hear the mosaic as one item.
 * - **Paid for**: its photos and videos as a [MediaAlbum], each opening in the media viewer by the id
 *   [richMediaId] gives it ([onOpen]), with "Bought · 25 ★" over its top start corner.
 *
 * The caption, if any, is the bubble's.
 */
@Suppress("LongParameterList") // The post, its message, how it opens and downloads, and the bubble's colors.
@Composable
internal fun MessagePaidMedia(
    content: MessageContent.PaidMedia,
    messageId: Long,
    colors: BubbleColors,
    onOpen: (mediaId: Long) -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    downloads: MediaDownloads = MediaDownloads(),
    onDownload: (fileId: Int) -> Unit = {},
    onCancelDownload: (fileId: Int) -> Unit = {},
) {
    Column(modifier) {
        if (content.isUnlocked) {
            UnlockedPaidMedia(content, messageId, onOpen, shape, downloads, onDownload, onCancelDownload)
        } else {
            LockedPaidMedia(content, shape)
            PaidNote(colors)
        }
    }
}

@Suppress("LongParameterList") // The post, its message, how it opens and downloads.
@Composable
private fun UnlockedPaidMedia(
    content: MessageContent.PaidMedia,
    messageId: Long,
    onOpen: (mediaId: Long) -> Unit,
    shape: CornerBasedShape,
    downloads: MediaDownloads,
    onDownload: (fileId: Int) -> Unit,
    onCancelDownload: (fileId: Int) -> Unit,
) {
    val media = remember(content) { content.mediaContents() }
    val items = remember(media) { media.map { it.second }.toImmutableList() }
    val ids = remember(media, messageId) { media.map { richMediaId(messageId, it.first) }.toImmutableList() }
    val locale = LocalConfiguration.current.locales[0]
    val stars = remember(content.starCount, locale) { giveawayNumber(content.starCount, locale) }
    val spokenStars = pluralStringResource(R.plurals.paid_media_stars, content.starCount.toPluralCount(), stars)
    val bought = stringResource(R.string.paid_media_bought_description, spokenStars)
    Box {
        MediaAlbum(
            items = items,
            messageIds = ids,
            onOpen = { index -> onOpen(ids[index]) },
            shape = shape,
            downloads = downloads,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
        )
        StarPill(
            text = stringResource(R.string.paid_media_bought, stars),
            containerColor = MaterialTheme.pingogramColors.mediaOverlay,
            contentColor = MaterialTheme.pingogramColors.onMediaOverlay,
            textStyle = MaterialTheme.typography.labelMediumEmphasized,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(MaterialTheme.spacing.extraSmall)
                    .semantics { contentDescription = bought },
        )
    }
}

/** The blurred previews under a scrim, the price and the count; one item for screen readers, nothing tappable. */
@Composable
private fun LockedPaidMedia(
    content: MessageContent.PaidMedia,
    shape: CornerBasedShape,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.roundToPx() }
    val gapPx = with(density) { MaterialTheme.sizes.mediaAlbumGap.roundToPx() }
    val shown = if (content.media.size > MAX_ALBUM_ITEMS) content.media.subList(0, MAX_ALBUM_ITEMS) else content.media
    val layout = remember(shown, widthPx, gapPx) { albumLayout(shown.map(::previewAspectRatio), widthPx, gapPx) }
    val cellShape = MaterialTheme.shapes.extraSmall
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val fallback = MaterialTheme.colorScheme.surfaceContainerHighest
    val dim = MaterialTheme.pingogramColors.mediaColorFilter
    val locale = LocalConfiguration.current.locales[0]
    val stars = remember(content.starCount, locale) { giveawayNumber(content.starCount, locale) }
    val count = paidMediaCountText(content)
    val spokenStars = pluralStringResource(R.plurals.paid_media_stars, content.starCount.toPluralCount(), stars)
    val description = stringResource(R.string.paid_media_locked_description, count, spokenStars)
    Box(
        Modifier
            .clip(shape)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Layout(
            content = {
                shown.forEachIndexed { index, item ->
                    val cell = layout.cells[index]
                    val itemShape =
                        remember(cell, layout, shape, cellShape, ltr) {
                            albumItemShape(cell, layout, shape, cellShape, ltr)
                        }
                    BlurredMinithumbnail(
                        minithumbnail = (item as? PaidMediaItem.Preview)?.minithumbnail,
                        fallback = fallback,
                        modifier = Modifier.clip(itemShape),
                        colorFilter = dim,
                    )
                }
            },
            measurePolicy = remember(layout) { AlbumMeasurePolicy(layout) },
        )
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        )
        Column(
            modifier = Modifier.align(Alignment.Center).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            StarPill(
                text = stringResource(R.string.paid_media_price, stars),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                textStyle = MaterialTheme.typography.titleSmallEmphasized,
                leadingIcon = UiR.drawable.ic_lock,
            )
            MediaLabel(count)
        }
    }
}

/** A pill with [text] and the star symbol after it, and [leadingIcon] before it where given. */
@Composable
private fun StarPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    Row(
        modifier =
            modifier
                .background(containerColor, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                tint = contentColor,
            )
        }
        Text(text = text, color = contentColor, maxLines = 1, style = textStyle.copy(fontFeatureSettings = "tnum"))
        Icon(
            painter = painterResource(UiR.drawable.ic_star_filled),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            tint = contentColor,
        )
    }
}

/** Under a locked post: that Pingogram cannot pay for it, and where to unlock it, on the bubble's block color. */
@Composable
private fun PaidNote(colors: BubbleColors) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                .background(colors.block, MaterialTheme.shapes.medium)
                .padding(MaterialTheme.spacing.small)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            tint = colors.blockContent,
        )
        Text(
            text = stringResource(R.string.paid_media_note),
            color = colors.blockContent,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "3 photos", "2 videos" or "5 photos and videos". */
@Composable
private fun paidMediaCountText(content: MessageContent.PaidMedia): String {
    val (photos, videos) = content.mediaCounts()
    return when {
        videos == 0 -> pluralStringResource(R.plurals.paid_media_photos, photos, photos)
        photos == 0 -> pluralStringResource(R.plurals.paid_media_videos, videos, videos)
        else -> pluralStringResource(R.plurals.paid_media_items, photos + videos, photos + videos)
    }
}

/** How many photos and how many videos the post holds, previews counted by their kind. */
internal fun MessageContent.PaidMedia.mediaCounts(): Pair<Int, Int> {
    val videos =
        media.count { item ->
            when (item) {
                is PaidMediaItem.Preview -> item.isVideo
                is PaidMediaItem.Video -> true
                is PaidMediaItem.Photo, PaidMediaItem.Unsupported -> false
            }
        }
    return (media.size - videos) to videos
}

/** Width / height of a preview; 1 for media of other kinds, whose size is unknown. */
private fun previewAspectRatio(item: PaidMediaItem): Float {
    val preview = item as? PaidMediaItem.Preview ?: return 1f
    return if (preview.width > 0 && preview.height > 0) preview.width.toFloat() / preview.height else 1f
}

private const val SCRIM_ALPHA = 0.32f

@Preview
@Composable
private fun MessagePaidMediaPreview() {
    PingogramTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            MessagePaidMedia(
                content = PaidPostSamples.paidMedia(unlocked = false),
                messageId = 1,
                colors = MaterialTheme.pingogramColors.incomingBubble,
                onOpen = {},
            )
            MessagePaidMedia(
                content = PaidPostSamples.paidMedia(unlocked = true),
                messageId = 2,
                colors = MaterialTheme.pingogramColors.incomingBubble,
                onOpen = {},
            )
        }
    }
}
