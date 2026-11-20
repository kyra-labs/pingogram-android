package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R

/**
 * A profile's story albums (board 11a) at the top of its Stories tab: "Albums · 4" in `titleSmallEmphasized`
 * `primary` (a heading) over the covers, two to a row; [onOpenAlbum] opens one.
 */
@Composable
internal fun StoryAlbumsContent(
    state: StoryAlbumsUiState.Content,
    onOpenAlbum: (albumId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = stringResource(R.string.story_albums_header, state.albums.size),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        // Two to a row without a lazy grid, as a profile holds a few albums and is lazy itself.
        state.albums.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                row.forEach { album -> AlbumCover(album, onOpenAlbum, Modifier.weight(1f)) }
                repeat(COLUMNS - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

/** One album: its cover with `largeIncreased` corners over its name and its count, one button for screen readers. */
@Composable
private fun AlbumCover(
    album: StoryAlbumTile,
    onOpenAlbum: (albumId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = album.count
    val description =
        if (count == null) {
            stringResource(R.string.story_album_cover_description, album.name)
        } else {
            pluralStringResource(R.plurals.story_album_cover_description_count, count, album.name, count)
        }
    val open = stringResource(R.string.story_album_open)
    val sizePx = with(LocalDensity.current) { (MaterialTheme.sizes.sharedMediaCell * 2).roundToPx() }
    Column(
        modifier
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(onClickLabel = open, role = Role.Button) { onOpenAlbum(album.id) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(open) {
                    onOpenAlbum(album.id)
                    true
                }
            },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        AlbumImage(
            picture = album.cover,
            sizePx = sizePx,
            modifier = Modifier.fillMaxWidth().aspectRatio(COVER_RATIO).clip(MaterialTheme.shapes.largeIncreased),
        )
        // Clear of the tile's rounded corners, which clip its ripple.
        Column(
            Modifier.padding(horizontal = MaterialTheme.spacing.small).padding(bottom = MaterialTheme.spacing.small),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall.userContent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (count != null) {
                Text(
                    text = pluralStringResource(R.plurals.story_album_cover_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val COLUMNS = 2

/** A cover's width to its height, as the board draws it. */
private const val COVER_RATIO = 1.2f
