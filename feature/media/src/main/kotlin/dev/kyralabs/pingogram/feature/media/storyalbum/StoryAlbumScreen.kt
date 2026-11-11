package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * An opened story album (board 11b, `docs/DESIGN_SYSTEM.md` §6 "Story albums"): a small [TopAppBar] with back and the
 * album's name on `surfaceContainer`; on a [ContentSheet], "Neha Kulkarni · 9 stories" over the stories in a keyed
 * grid of 9:16 tiles with `large` corners, three across a phone, each with its day on a `mediaOverlay` pill; a tap
 * plays the album from that story ([onOpenStory]). The next page loads [LOAD_MORE_AHEAD] tiles before the end, a
 * [LoadingIndicator] below while it comes, its failure with "Try again". Loading is a [DelayedLoadingIndicator]; an
 * empty album and a failure are [EmptyState]s.
 */
@Composable
internal fun StoryAlbumScreen(
    state: StoryAlbumUiState,
    onEvent: (StoryAlbumEvent) -> Unit,
    onBack: () -> Unit,
    onOpenStory: (storyId: Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { AlbumTopBar((state as? StoryAlbumUiState.Content)?.name.orEmpty(), onBack) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            AlbumBody(state, onEvent, onOpenStory, gridState, contentPadding)
        }
    }
}

/** Back and the album's name (one line, a heading), at the window margin, as Recent calls' bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTopBar(
    name: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = name,
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized.userContent,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.story_album_back),
                )
            }
        },
        modifier = Modifier.padding(horizontal = windowMargin() - MaterialTheme.spacing.windowMarginCompact),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        expandedHeight = MaterialTheme.sizes.denseTopAppBar,
    )
}

/** What the sheet shows for [state]; its own composable, so the frames the `Scaffold` measures anew stop here. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumBody(
    state: StoryAlbumUiState,
    onEvent: (StoryAlbumEvent) -> Unit,
    onOpenStory: (storyId: Int) -> Unit,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
) {
    val bottom = Modifier.padding(contentPadding)
    when (state) {
        StoryAlbumUiState.Loading -> {
            Box(bottom.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        is StoryAlbumUiState.Error -> {
            EmptyState(
                illustration = MaterialShapes.Cookie4Sided,
                icon = UiR.drawable.ic_error,
                title = stringResource(R.string.story_album_error),
                body = errorText(state.error),
                modifier = bottom,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    Button(onClick = { onEvent(StoryAlbumEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_album_try_again))
                    }
                },
            )
        }

        is StoryAlbumUiState.Content -> {
            if (state.stories.isEmpty() && state.endReached) {
                EmptyState(
                    illustration = MaterialShapes.Cookie9Sided,
                    icon = UiR.drawable.ic_web_stories_filled,
                    title = stringResource(R.string.story_album_empty),
                    body = stringResource(R.string.story_album_empty_body),
                    modifier = bottom,
                )
            } else {
                AlbumGrid(state, onEvent, onOpenStory, gridState, contentPadding)
            }
        }
    }
}

/** The poster and the count over the stories' tiles, then the next page's state. */
@Composable
private fun AlbumGrid(
    state: StoryAlbumUiState.Content,
    onEvent: (StoryAlbumEvent) -> Unit,
    onOpenStory: (storyId: Int) -> Unit,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - LOAD_MORE_AHEAD
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnEvent(StoryAlbumEvent.LoadMore) }
    }
    val spacing = MaterialTheme.spacing
    val margin = windowMargin()
    val sizePx = with(LocalDensity.current) { (MaterialTheme.sizes.sharedMediaCell * 2).roundToPx() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(horizontal = margin),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item(key = HEADER, contentType = HEADER, span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.story_album_subtitle,
                        state.totalCount,
                        state.posterName,
                        state.totalCount,
                    ),
                style = MaterialTheme.typography.bodyMedium.userContent,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.extraSmall),
            )
        }
        items(state.stories, key = { it.id }, contentType = { STORY }) { story ->
            StoryTile(story, sizePx, onOpenStory, Modifier.animateItem())
        }
        footer(state, onEvent)
    }
}

/** The next page's [LoadingIndicator], or why it failed with "Try again", across the grid. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyGridScope.footer(
    state: StoryAlbumUiState.Content,
    onEvent: (StoryAlbumEvent) -> Unit,
) {
    val error = state.moreError
    when {
        state.loadingMore -> {
            item(key = FOOTER, contentType = FOOTER, span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }

        error != null -> {
            item(key = FOOTER, contentType = FOOTER, span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorText(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { onEvent(StoryAlbumEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_album_try_again))
                    }
                }
            }
        }
    }
}

/** One story: its picture cropped to 9:16 with `large` corners and its day on a pill; one button for screen readers. */
@Composable
private fun StoryTile(
    story: AlbumStory,
    sizePx: Int,
    onOpenStory: (storyId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            if (story.isVideo) R.string.story_album_video_description else R.string.story_album_story_description,
            story.date,
        )
    val play = stringResource(R.string.story_album_play)
    Box(
        modifier
            .aspectRatio(STORY_RATIO)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = play, role = Role.Button) { onOpenStory(story.id) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(play) {
                    onOpenStory(story.id)
                    true
                }
            },
    ) {
        AlbumImage(story.picture, sizePx, Modifier.fillMaxSize())
        Text(
            text = story.date,
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = MaterialTheme.pingogramColors.onMediaOverlay,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacing.small)
                    .clip(CircleShape)
                    .background(MaterialTheme.pingogramColors.mediaOverlay)
                    .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        )
    }
}

private const val HEADER = "header"
private const val STORY = "story"
private const val FOOTER = "footer"

/** How many tiles before the end the next page is asked for. */
private const val LOAD_MORE_AHEAD = 6

/** A story's width to its height. */
private const val STORY_RATIO = 9f / 16f
