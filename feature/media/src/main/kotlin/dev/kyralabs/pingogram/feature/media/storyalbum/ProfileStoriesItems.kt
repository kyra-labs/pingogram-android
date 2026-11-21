package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.ItemMotion
import dev.kyralabs.pingogram.core.ui.component.ListNotice
import dev.kyralabs.pingogram.core.ui.component.NoticeBadge
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The items a profile adds to its grid for its Stories tab, [margin] in from the grid's sides, moving on [motion]. */
typealias ProfileStoriesItems = LazyGridScope.(margin: Dp, motion: ItemMotion) -> Unit

/**
 * A profile's Stories tab (owner decision 2026-11-20, `docs/DESIGN_SYSTEM.md` §6 "Story albums") for chat [chatId],
 * read once the tab is [active], as the items the profile adds to its own grid below its tabs (`feature:profile`
 * cannot use this module, so `:app` hands it over). A story tile plays the profile's stories from it ([onOpenStory]),
 * an album cover opens the album ([onOpenAlbum]). On the user's own profile, "New album" asks for the album's name
 * ([AlbumNameDialog]) and goes on to picking its stories ([onCreateAlbum]); the tab is read again as it shows again.
 * Its own view model, keyed by the chat.
 */
@Composable
fun rememberProfileStoriesItems(
    chatId: Long,
    active: Boolean,
    onOpenStory: (storyId: Int) -> Unit,
    onOpenAlbum: (albumId: Int) -> Unit,
    onCreateAlbum: (name: String) -> Unit = {},
    viewModel: ProfileStoriesViewModel = hiltViewModel(key = "profileStories-$chatId"),
): ProfileStoriesItems {
    LaunchedEffect(viewModel, chatId, active) { if (active) viewModel.start(chatId) }
    // Not on the first start, which reads the tab already.
    val starts = remember { intArrayOf(0) }
    LifecycleStartEffect(viewModel) {
        if (starts[0]++ > 0) viewModel.onEvent(ProfileStoriesEvent.Refresh)
        onStopOrDispose {}
    }
    var naming by rememberSaveable { mutableStateOf(false) }
    val currentOnCreateAlbum by rememberUpdatedState(onCreateAlbum)
    if (naming) {
        AlbumNameDialog(
            title = R.string.story_album_new_title,
            confirm = R.string.story_album_new_next,
            onConfirm = { name ->
                naming = false
                currentOnCreateAlbum(name)
            },
            onDismiss = { naming = false },
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    val currentOnOpenAlbum by rememberUpdatedState(onOpenAlbum)
    val actions =
        remember(viewModel) {
            ProfileStoriesActions(
                viewModel::onEvent,
                { currentOnOpenStory(it) },
                { currentOnOpenAlbum(it) },
                onNewAlbum = { naming = true },
            )
        }
    val sizePx = with(LocalDensity.current) { (MaterialTheme.sizes.sharedMediaCell * 2).roundToPx() }
    return remember(state, actions, sizePx) {
        {
            margin,
            motion,
            ->
            profileStories(state, actions, sizePx, margin, motion)
        }
    }
}

/** Where the Stories tab's events and taps go. */
@Immutable
class ProfileStoriesActions(
    val onEvent: (ProfileStoriesEvent) -> Unit,
    val onOpenStory: (storyId: Int) -> Unit,
    val onOpenAlbum: (albumId: Int) -> Unit,
    val onNewAlbum: () -> Unit = {},
)

/**
 * The Stories tab's items for [state]: the delayed loading indicator; "No stories yet"; why the stories failed with
 * "Try again"; or the album covers with their counts across the grid, then the stories as 9:16 tiles with their days,
 * one to a cell with the media cells' gap, decoded at [sizePx], and their next page asked for as the end shows.
 */
internal fun LazyGridScope.profileStories(
    state: ProfileStoriesUiState,
    actions: ProfileStoriesActions,
    sizePx: Int,
    margin: Dp,
    motion: ItemMotion,
) {
    when (state) {
        ProfileStoriesUiState.Loading -> {
            fullSpan(STATE, motion) { StoriesLoading() }
        }

        ProfileStoriesUiState.Empty -> {
            fullSpan(STATE, motion) { StoriesEmpty() }
        }

        is ProfileStoriesUiState.Error -> {
            fullSpan(STATE, motion) { StoriesError(state, actions) }
        }

        is ProfileStoriesUiState.Content -> {
            if (state.canCreateAlbum) {
                fullSpan(
                    NEW_ALBUM,
                    motion,
                ) { NewAlbumButton(actions.onNewAlbum, Modifier.padding(horizontal = margin)) }
            }
            if (state.albums.isNotEmpty()) {
                fullSpan(ALBUMS, motion) {
                    StoryAlbumsContent(
                        StoryAlbumsUiState.Content(state.albums),
                        actions.onOpenAlbum,
                        Modifier.padding(horizontal = margin, vertical = MaterialTheme.spacing.medium),
                    )
                }
            }
            items(state.stories, key = { "$STORY${it.id}" }, contentType = { STORY }) { story ->
                // Apart by the album's gap, as the profile's media cells are.
                val tile = Modifier.animateItem(this, motion).padding(MaterialTheme.sizes.mediaAlbumGap / 2)
                StoryTile(story, sizePx, actions.onOpenStory, tile)
            }
            footer(state, actions)
        }
    }
}

/** The next page: asked for once the end shows, its indicator while it comes, or why it failed with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyGridScope.footer(
    state: ProfileStoriesUiState.Content,
    actions: ProfileStoriesActions,
) {
    val error = state.moreError
    if (state.endReached && error == null) return
    item(key = FOOTER, contentType = FOOTER, span = { GridItemSpan(maxLineSpan) }) {
        if (error != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorText(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { actions.onEvent(ProfileStoriesEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.story_album_try_again))
                }
            }
        } else {
            // Composed only as the end of the grid comes into view (`docs/PERFORMANCE.md` §3).
            LaunchedEffect(state.stories.size) { actions.onEvent(ProfileStoriesEvent.LoadMore) }
            Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun StoriesLoading() {
    val description = stringResource(R.string.profile_stories_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoriesEmpty() {
    val colors = MaterialTheme.colorScheme
    ListNotice(
        badge =
            NoticeBadge(
                MaterialShapes.Cookie9Sided,
                UiR.drawable.ic_web_stories_filled,
                colors.primaryContainer,
                colors.onPrimaryContainer,
            ),
        title = stringResource(R.string.profile_stories_empty),
        body = stringResource(R.string.profile_stories_empty_body),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoriesError(
    state: ProfileStoriesUiState.Error,
    actions: ProfileStoriesActions,
) {
    val colors = MaterialTheme.colorScheme
    ListNotice(
        badge =
            NoticeBadge(
                MaterialShapes.Cookie4Sided,
                UiR.drawable.ic_error,
                colors.errorContainer,
                colors.onErrorContainer,
            ),
        title = stringResource(R.string.profile_stories_error),
        body = errorText(state.error),
        // A failure is announced at once, as the shared media tabs' are.
        announce = LiveRegionMode.Assertive,
    ) {
        Button(onClick = { actions.onEvent(ProfileStoriesEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.story_album_try_again))
        }
    }
}

/** "New album" on the user's own Stories tab: a tonal button with the add symbol, at the start of the grid. */
@Composable
private fun NewAlbumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.padding(top = MaterialTheme.spacing.medium),
    ) {
        Icon(painterResource(UiR.drawable.ic_add), contentDescription = null)
        Text(stringResource(R.string.story_album_new), Modifier.padding(start = MaterialTheme.spacing.small))
    }
}

/** A full-width item that comes, moves and goes on [motion]'s springs, as the profile's tabs' items do. */
private fun LazyGridScope.fullSpan(
    key: String,
    motion: ItemMotion,
    content: @Composable LazyGridItemScope.() -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) {
        Box(Modifier.animateItem(this, motion)) { content() }
    }
}

// Keys and content types, prefixed as they share the profile's grid with its other items.
private const val STATE = "stories:state"
private const val ALBUMS = "stories:albums"
private const val NEW_ALBUM = "stories:new-album"
private const val STORY = "stories:story:"
private const val FOOTER = "stories:footer"
