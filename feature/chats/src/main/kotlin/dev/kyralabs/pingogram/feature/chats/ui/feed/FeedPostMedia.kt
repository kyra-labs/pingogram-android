package dev.kyralabs.pingogram.feature.chats.ui.feed

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a feed post shows besides its text (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"), as wide as the card with
 * `large` corners: a photo, an album as two by two pictures (the last saying "+N"), a video's still picture with its
 * play symbol and length, a file card, or a poll's results. Pictures are decorative to screen readers, which read the
 * card's text.
 */
@Composable
internal fun FeedPostMedia(media: FeedMedia) {
    when (media) {
        is FeedMedia.Photo -> Picture(media.fileId, Modifier.aspectRatio(media.width.toFloat() / media.height))
        is FeedMedia.Album -> Album(media)
        is FeedMedia.Video -> Video(media)
        is FeedMedia.File -> File(media)
        is FeedMedia.Poll -> Poll(media)
    }
}

/** A TDLib file's picture filling [modifier]'s bounds, clipped to `large` corners. */
@Composable
private fun Picture(
    fileId: Int?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = fileId?.let(::TdlibFileImage),
        contentDescription = null,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = MaterialTheme.sizes.mediaMaxHeight)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}

/** An album's first four pictures, two by two, the last with "+N" on the media overlay. */
@Composable
private fun Album(album: FeedMedia.Album) {
    val gap = MaterialTheme.sizes.mediaAlbumGap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        album.fileIds.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEachIndexed { column, fileId ->
                    val isLast = row * 2 + column == album.fileIds.lastIndex
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        Picture(fileId, Modifier.fillMaxSize())
                        if (isLast && album.more > 0) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.pingogramColors.mediaOverlay),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.feed_album_more, album.more),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A video's still picture, its play symbol on a `Cookie9Sided` overlay badge, and its length on an overlay pill. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Video(video: FeedMedia.Video) {
    val overlay = MaterialTheme.pingogramColors.mediaOverlay
    val onOverlay = MaterialTheme.colorScheme.inverseOnSurface
    Box(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT), contentAlignment = Alignment.Center) {
        Picture(video.thumbnailFileId, Modifier.fillMaxSize())
        Box(
            Modifier
                .size(MaterialTheme.sizes.playBadge)
                .background(overlay, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UiR.drawable.ic_play_arrow_filled), contentDescription = null, tint = onOverlay)
        }
        Text(
            text = duration(video.duration),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacing.small)
                    .background(overlay, CircleShape)
                    .padding(horizontal = MaterialTheme.spacing.small),
            color = onOverlay,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** A file: the download symbol on a `primaryContainer` tile, its name (cut in the middle) over its size. */
@Composable
private fun File(file: FeedMedia.File) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerHighest, MaterialTheme.shapes.large)
                .padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MaterialTheme.sizes.avatarSmall)
                .background(colors.primaryContainer, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UiR.drawable.ic_download), contentDescription = null, tint = colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.bodyLarge.userContent,
            )
            Text(fileSizeText(file.size), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A poll's question, each option's share as a bar (the user's vote ticked), and how many voted. */
@Composable
private fun Poll(poll: FeedMedia.Poll) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(poll.question.text, style = MaterialTheme.typography.titleSmall.userContent)
        Text(
            text = pollVoters(poll),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        poll.options.forEach { option ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feed_poll_share, option.percentage),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(Modifier.weight(1f)) {
                    Text(option.text.text, style = MaterialTheme.typography.bodyMedium.userContent)
                    LinearProgressIndicator(
                        progress = { option.percentage / PERCENT },
                        modifier = Modifier.fillMaxWidth().height(MaterialTheme.sizes.pollBar),
                        color = colors.primaryContainer,
                        trackColor = colors.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
                if (option.isChosen) {
                    Icon(
                        painterResource(UiR.drawable.ic_check_circle_filled),
                        contentDescription = null,
                        modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}

/** "Anonymous poll · 1,284 votes", or the same without "Anonymous". */
@Composable
private fun pollVoters(poll: FeedMedia.Poll): String {
    val locale = LocalConfiguration.current.locales[0]
    val count = remember(poll.voterCount, locale) { NumberFormat.getIntegerInstance(locale).format(poll.voterCount) }
    val votes = pluralStringResource(R.plurals.feed_poll_votes, poll.voterCount, count)
    return if (poll.isAnonymous) stringResource(R.string.feed_poll_anonymous, votes) else votes
}

/** [bytes] as "38.2 MB", as Android writes file sizes. */
@Composable
private fun fileSizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** [seconds] as "2:31", or "1:02:31" past an hour. */
private fun duration(seconds: Int): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

private const val VIDEO_ASPECT = 16f / 9f
private const val PERCENT = 100f
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
