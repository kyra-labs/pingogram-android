package dev.kyralabs.pingogram.feature.profile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A photo, video or GIF of the grid: its image square, cropped, in `extraSmall` corners with the album's gap between
 * cells (`sizes.mediaAlbumGap`), dimmed in dark theme like the conversation's media (§10), a video's length or "GIF"
 * on a label at its bottom end. One button for screen readers: "Video, 0:42, Mar 17".
 */
@Composable
internal fun LazyGridItemScope.MediaCell(
    item: SharedMediaItem.Media,
    cellSizePx: Int,
    onClick: () -> Unit,
) {
    val resources = LocalResources.current
    val open = stringResource(R.string.profile_shared_media_open)
    val fileId =
        remember(item, cellSizePx) {
            item.photo?.fileIdFor(cellSizePx) ?: item.thumbnail?.imageFileId
        }
    Box(
        modifier =
            Modifier
                .animateItem()
                .aspectRatio(1f)
                .padding(MaterialTheme.sizes.mediaAlbumGap / 2)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = open, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = item.description(resources) },
    ) {
        ProfileImage(
            fileId = fileId,
            sizePx = cellSizePx,
            modifier = Modifier.fillMaxSize(),
            colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
        )
        val label =
            when (item.kind) {
                MediaKind.Video -> item.duration
                MediaKind.Gif -> stringResource(R.string.profile_media_gifs)
                MediaKind.Photo -> null
            }
        if (label !=
            null
        ) {
            MediaLabel(label, Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.extraSmall))
        }
    }
}

/** What screen readers hear for a cell of the grid. */
private fun SharedMediaItem.Media.description(resources: android.content.res.Resources): String =
    when (kind) {
        MediaKind.Photo -> resources.getString(R.string.profile_media_photo, date)
        MediaKind.Video -> resources.getString(R.string.profile_media_video, date, duration.orEmpty())
        MediaKind.Gif -> resources.getString(R.string.profile_media_gif, date)
    }

/** A label over media: `labelSmall` in `onMediaOverlay` on a pill of `mediaOverlay`, legible on any picture. */
@Composable
private fun MediaLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    Text(
        text = text,
        modifier =
            modifier
                .clearAndSetSemantics {}
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        color = colors.onMediaOverlay,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * A file, song or voice message: its kind's icon in a tonal circle (`secondaryContainer`, the list item's leading
 * avatar size), its name, and its performer, length or size with the date; it opens in the chat at its message.
 */
@Composable
internal fun FileRow(
    item: SharedMediaItem.File,
    shapes: androidx.compose.material3.ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val size =
        remember(item.size) {
            android.text.format.Formatter
                .formatShortFileSize(context, item.size)
        }
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = profileRowColors(),
        leadingContent = { KindIcon(item.kind.icon) },
        supportingContent = {
            Text(
                text = stringResource(R.string.profile_media_detail, item.detail ?: size, item.date),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        },
    ) {
        Text(
            text = item.title.resolve(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/** A link: its title over its description, with the link icon; it opens in the browser. */
@Composable
internal fun LinkRow(
    item: SharedMediaItem.Link,
    shapes: androidx.compose.material3.ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = profileRowColors(),
        leadingContent = { KindIcon(UiR.drawable.ic_link) },
        supportingContent = {
            Text(
                text = stringResource(R.string.profile_media_detail, item.description, item.date),
                maxLines = LINK_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        },
    ) {
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/** A row's icon in a tonal circle of `secondaryContainer`, as wide as a list item's leading avatar. Decorative. */
@Composable
private fun KindIcon(
    @DrawableRes icon: Int,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(
                    MaterialTheme.sizes.leadingIconContainer,
                ).background(colors.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = colors.onSecondaryContainer)
    }
}

private val FileKind.icon: Int
    get() =
        when (this) {
            FileKind.Document -> UiR.drawable.ic_description_filled
            FileKind.Music -> UiR.drawable.ic_audio_file_filled
            FileKind.Voice -> UiR.drawable.ic_mic_filled
        }

/** Lines of a link's description. */
private const val LINK_DESCRIPTION_LINES = 2
