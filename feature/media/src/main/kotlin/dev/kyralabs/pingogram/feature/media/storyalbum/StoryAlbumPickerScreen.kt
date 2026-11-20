package dev.kyralabs.pingogram.feature.media.storyalbum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Picking stories for one of the user's albums (owner decision 2026-11-20, `docs/DESIGN_SYSTEM.md` §6 "Story
 * albums"): a small [TopAppBar] with close ([onClose]), what the stories are for ("New album", "Add stories", "Remove
 * stories") and how many are picked; on a [ContentSheet], the stories as the album's 9:16 tiles, each a checkbox with
 * its day and a check at its top end; below, the [Button] that makes the change, enabled once one is picked. Loading
 * a [DelayedLoadingIndicator]; nothing to pick, and a failure, [EmptyState]s.
 */
@Composable
internal fun StoryAlbumPickerScreen(
    state: StoryAlbumPickerUiState,
    onEvent: (StoryAlbumPickerEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? StoryAlbumPickerUiState.Content
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { PickerTopBar(content, onClose) },
        bottomBar = { if (content != null && content.stories.isNotEmpty()) ConfirmBar(content, onEvent) },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            PickerBody(state, onEvent, contentPadding)
        }
    }
}

/** Close, then what picking is for over how many are picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerTopBar(
    content: StoryAlbumPickerUiState.Content?,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(content?.purpose.title()),
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                if (content != null && content.stories.isNotEmpty()) {
                    val picked = content.selected.size
                    Text(
                        text = pluralStringResource(R.plurals.story_album_picked, picked, picked),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.story_album_picker_close))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickerBody(
    state: StoryAlbumPickerUiState,
    onEvent: (StoryAlbumPickerEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val body = Modifier.padding(contentPadding)
    when (state) {
        StoryAlbumPickerUiState.Loading -> {
            Box(body.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        is StoryAlbumPickerUiState.Error -> {
            EmptyState(
                illustration = MaterialShapes.Cookie4Sided,
                icon = UiR.drawable.ic_error,
                title = stringResource(R.string.story_album_picker_error),
                body = errorText(state.error),
                modifier = body,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    Button(onClick = { onEvent(StoryAlbumPickerEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_album_try_again))
                    }
                },
            )
        }

        is StoryAlbumPickerUiState.Content -> {
            if (state.stories.isEmpty()) {
                EmptyState(
                    illustration = MaterialShapes.Cookie9Sided,
                    icon = UiR.drawable.ic_web_stories_filled,
                    title = stringResource(R.string.story_album_picker_none),
                    body = stringResource(R.string.story_album_picker_none_body),
                    modifier = body,
                )
            } else {
                PickerGrid(state, onEvent, contentPadding)
            }
        }
    }
}

/** The stories as 9:16 tiles, three across a phone, keyed by story. */
@Composable
private fun PickerGrid(
    state: StoryAlbumPickerUiState.Content,
    onEvent: (StoryAlbumPickerEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val spacing = MaterialTheme.spacing
    val sizePx = with(LocalDensity.current) { (MaterialTheme.sizes.sharedMediaCell * 2).roundToPx() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell),
        modifier = Modifier.fillMaxSize().padding(horizontal = windowMargin()),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items(state.stories, key = { it.id }) { story ->
            PickableStory(story, story.id in state.selected, sizePx) { onEvent(StoryAlbumPickerEvent.Toggle(story.id)) }
        }
    }
}

/**
 * One story to pick: its picture cropped to 9:16 with `large` corners, its day on a `mediaOverlay` pill, and at its
 * top end an empty ring on `mediaOverlay` or, picked, a filled check in `primary`; one checkbox for screen readers.
 */
@Composable
private fun PickableStory(
    story: AlbumStory,
    isPicked: Boolean,
    sizePx: Int,
    onToggle: () -> Unit,
) {
    val description =
        stringResource(
            if (story.isVideo) R.string.story_album_video_description else R.string.story_album_story_description,
            story.date,
        )
    val colors = MaterialTheme.colorScheme
    val overlay = MaterialTheme.pingogramColors
    Box(
        Modifier
            .aspectRatio(STORY_RATIO)
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = isPicked, role = Role.Checkbox, onValueChange = { onToggle() })
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Checkbox
                toggleableState = ToggleableState(isPicked)
            },
    ) {
        AlbumImage(story.picture, sizePx, Modifier.fillMaxSize())
        Icon(
            painter =
                painterResource(
                    if (isPicked) UiR.drawable.ic_check_circle_filled else UiR.drawable.ic_radio_button_unchecked,
                ),
            contentDescription = null,
            tint = if (isPicked) colors.primary else overlay.onMediaOverlay,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(MaterialTheme.spacing.small)
                    .background(if (isPicked) colors.onPrimary else overlay.mediaOverlay, CircleShape),
        )
        Text(
            text = story.date,
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = overlay.onMediaOverlay,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacing.small)
                    .clip(CircleShape)
                    .background(overlay.mediaOverlay)
                    .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        )
    }
}

/** The change's button, full width at the bottom: "Create album", "Add" or "Remove", once a story is picked. */
@Composable
private fun ConfirmBar(
    content: StoryAlbumPickerUiState.Content,
    onEvent: (StoryAlbumPickerEvent) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.medium),
    ) {
        Button(
            onClick = { onEvent(StoryAlbumPickerEvent.Confirm) },
            enabled = content.canConfirm,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(content.purpose.confirm()))
        }
    }
}

/** What picking is for, as the top bar names it; "Pick stories" before it is known. */
private fun StoryAlbumPickPurpose?.title(): Int =
    when (this) {
        StoryAlbumPickPurpose.Create -> R.string.story_album_picker_create_title
        StoryAlbumPickPurpose.Add -> R.string.story_album_picker_add_title
        StoryAlbumPickPurpose.Remove -> R.string.story_album_picker_remove_title
        null -> R.string.story_album_picker_title
    }

private fun StoryAlbumPickPurpose.confirm(): Int =
    when (this) {
        StoryAlbumPickPurpose.Create -> R.string.story_album_picker_create
        StoryAlbumPickPurpose.Add -> R.string.story_album_picker_add
        StoryAlbumPickPurpose.Remove -> R.string.story_album_picker_remove
    }

/** A story's width to its height. */
private const val STORY_RATIO = 9f / 16f
