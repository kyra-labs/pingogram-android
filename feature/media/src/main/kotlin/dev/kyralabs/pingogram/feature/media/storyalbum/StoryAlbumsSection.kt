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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R

/**
 * A chat's story albums (board 11a, `docs/DESIGN_SYSTEM.md` §6 "Story albums"), for a profile to show among its
 * sections: nothing while they load or when the chat has none; else "Albums · 4" in `titleSmallEmphasized` `primary`
 * (a heading) over the covers, two to a row. [onOpenAlbum] opens one. Its own view model, keyed by the chat, so the
 * profile's recomposes for none of it.
 */
@Composable
fun StoryAlbumsSection(
    chatId: Long,
    onOpenAlbum: (albumId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryAlbumsViewModel = hiltViewModel(key = "storyAlbums-$chatId"),
) {
    LaunchedEffect(viewModel, chatId) { viewModel.start(chatId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? StoryAlbumsUiState.Content
    // The albums grow in once loaded, moving what follows them on the profile smoothly; the last ones shown stay
    // while they go.
    val shown = remember { arrayOfNulls<StoryAlbumsUiState.Content>(1) }
    if (content != null) shown[0] = content
    GrowingVisibility(visible = content != null, modifier = modifier) {
        shown[0]?.let { StoryAlbumsContent(it, onOpenAlbum) }
    }
}

/** The section's header and covers for [state]; also drawn alone by previews and goldens. */
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

/** One album: its cover with `largeIncreased` corners over its name, one button for screen readers. */
@Composable
private fun AlbumCover(
    album: StoryAlbumTile,
    onOpenAlbum: (albumId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.story_album_cover_description, album.name)
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
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall.userContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Clear of the tile's rounded corners, which clip its ripple.
            modifier =
                Modifier
                    .padding(
                        horizontal = MaterialTheme.spacing.small,
                    ).padding(bottom = MaterialTheme.spacing.small),
        )
    }
}

private const val COLUMNS = 2

/** A cover's width to its height, as the board draws it. */
private const val COVER_RATIO = 1.2f
