package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Picking the photos and videos of a new story (board 10a, `docs/DESIGN_SYSTEM.md` §6 "New story"): the shown item
 * large, with its number and "Remove from story"; "In this story", the picked items in the order they will be posted,
 * dragged to reorder; that Android's photo picker chose them; then "Camera" and "Next". At most `contentMaxWidth`
 * wide, centred. [hasCamera] false leaves "Camera" out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewStoryScreen(
    state: NewStoryUiState,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
    hasCamera: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { NewStoryTopBar(state.items.size, onClose = { onEvent(NewStoryEvent.Close) }) },
        bottomBar = { NewStoryActions(state, hasCamera, onEvent) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            NewStoryBody(state, onEvent, Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewStoryTopBar(
    count: Int,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.new_story_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.new_story_close))
            }
        },
        actions = { if (count > 0) SelectedCount(count) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** "4 selected" on a `secondaryContainer` pill. */
@Composable
private fun SelectedCount(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
        modifier = Modifier.padding(end = MaterialTheme.spacing.medium),
    ) {
        Text(
            pluralStringResource(R.plurals.new_story_selected, count, count),
            style = MaterialTheme.typography.labelLargeEmphasized.tabular(),
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small,
                ),
        )
    }
}

@Composable
private fun NewStoryBody(
    state: NewStoryUiState,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier.fillMaxSize()) {
        ShownItem(
            state = state,
            onRemove = { key -> onEvent(NewStoryEvent.Remove(key)) },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spacing.medium),
        )
        StripHeader(reorderable = state.items.size > 1)
        PickStrip(state, onEvent)
        if (state.items.isNotEmpty()) PickerNote(state.items.size)
    }
}

/** The shown item, as large as the room allows, with its number and "Remove from story". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShownItem(
    state: NewStoryUiState,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Another item shown crossfades into its place on the effects springs.
    StateCrossfade(
        targetState = state.shown,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentKey = { it?.key },
        label = "shownItem",
    ) { shown ->
        Box(Modifier.fillMaxSize()) {
            when {
                shown != null -> {
                    ItemImage(shown.image, Modifier.fillMaxSize())
                    // An item being removed fades out without its number, which it no longer has.
                    val number = state.items.indexOf(shown) + 1
                    if (number > 0) {
                        NumberBadge(
                            number,
                            large = true,
                            Modifier.align(Alignment.TopStart).padding(MaterialTheme.spacing.medium),
                        )
                    }
                    if (shown.isVideo) {
                        DurationLabel(
                            shown.durationSeconds,
                            Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.medium),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onRemove(shown.key) },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacing.small),
                    ) {
                        Icon(painterResource(UiR.drawable.ic_delete), stringResource(R.string.new_story_remove))
                    }
                }

                state.preparing > 0 -> {
                    LoadingIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** "In this story", and "Drag to reorder" once there is more than one item. */
@Composable
private fun StripHeader(reorderable: Boolean) {
    val spacing = MaterialTheme.spacing
    Row(
        Modifier.fillMaxWidth().padding(start = spacing.medium, end = spacing.medium, top = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.new_story_in_this_story),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (reorderable) {
            Text(
                stringResource(R.string.new_story_drag_to_reorder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** That Android's photo picker chose the items, and Pingogram sees only those. */
@Composable
private fun PickerNote(count: Int) {
    val spacing = MaterialTheme.spacing
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.medium).padding(top = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            painterResource(UiR.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        )
        Text(
            pluralStringResource(R.plurals.new_story_picker_note, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Camera" (where the device has one) and "Next", at the medium button height the board gives them. */
@Composable
private fun NewStoryActions(
    state: NewStoryUiState,
    hasCamera: Boolean,
    onEvent: (NewStoryEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth().padding(spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            StoryActionButtons(state, hasCamera, onEvent)
        }
    }
}

/** "Camera" and "Next" in their row. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.StoryActionButtons(
    state: NewStoryUiState,
    hasCamera: Boolean,
    onEvent: (NewStoryEvent) -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    if (hasCamera) {
        FilledTonalButton(
            onClick = { onEvent(NewStoryEvent.TakePhoto) },
            enabled = state.remaining > 0,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.heightIn(min = height),
        ) {
            Icon(
                painterResource(UiR.drawable.ic_photo_camera_filled),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Text(
                stringResource(R.string.new_story_camera),
                style = ButtonDefaults.textStyleFor(height),
                modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
            )
        }
    }
    Button(
        onClick = { onEvent(NewStoryEvent.Next) },
        enabled = state.canGoNext,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = Modifier.weight(1f).heightIn(min = height),
    ) {
        Text(stringResource(R.string.new_story_next), style = ButtonDefaults.textStyleFor(height))
    }
}
