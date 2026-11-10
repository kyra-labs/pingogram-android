package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Composing a new story (board 10b, `docs/DESIGN_SYSTEM.md` §6 "New story"): the picked items full bleed, swiped
 * through, with a segment each at the top and "Back to picking"; below them, on a `surface` sheet with `extraLarge`
 * top corners, the caption's formatting bar and field, who the story is for and how long it stays, what the post
 * reaches, and "Post". The board's "Add text", "Add sticker" and "Draw" are left out (`docs/adr/0036`).
 */
@Composable
internal fun StoryComposeScreen(
    state: NewStoryUiState,
    compose: StoryComposeState,
    caption: CaptionText,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.scrim,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ItemPager(state, onEvent, Modifier.fillMaxSize())
                ComposeTopBar(state, onEvent, Modifier.align(Alignment.TopCenter).statusBarsPadding())
            }
            ComposeSheet(state, compose, caption, onEvent)
        }
    }
}

/** The picked items full bleed, swiped through; the shown one follows the pager, so the segments and summary do. */
@Composable
private fun ItemPager(
    state: NewStoryUiState,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = state.items.indexOf(state.shown).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { state.items.size }
    val items by rememberUpdatedState(state.items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            items.getOrNull(page)?.let { currentOnEvent(NewStoryEvent.Select(it.key)) }
        }
    }
    HorizontalPager(state = pager, modifier = modifier, key = { items[it].key }) { page ->
        val item = state.items[page]
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            ItemImage(item.image, Modifier.fillMaxSize())
            if (item.isVideo) {
                DurationLabel(
                    item.durationSeconds,
                    Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.medium),
                )
            }
        }
    }
}

/** A segment per item, the shown one full, and "Back to picking", on the media's overlay colors. */
@Composable
private fun ComposeTopBar(
    state: NewStoryUiState,
    onEvent: (NewStoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.pingogramColors
    val shown = state.items.indexOf(state.shown)
    Column(modifier.fillMaxWidth().padding(horizontal = spacing.small, vertical = spacing.small)) {
        if (state.items.size > 1) {
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.sizes.mediaAlbumGap),
            ) {
                state.items.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = MaterialTheme.sizes.pinnedIndicator)
                            .graphicsLayer { alpha = if (index == shown) 1f else TRACK_ALPHA }
                            .clip(CircleShape)
                            .background(colors.onMediaOverlay),
                    )
                }
            }
        }
        FilledIconButton(
            onClick = { onEvent(NewStoryEvent.Back) },
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.mediaOverlay,
                    contentColor = colors.onMediaOverlay,
                ),
            modifier = Modifier.padding(top = spacing.small),
        ) {
            Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.new_story_back_to_picking))
        }
    }
}

/** The caption, who the story is for and "Post", on a `surface` sheet with `extraLarge` top corners. */
@Composable
private fun ComposeSheet(
    state: NewStoryUiState,
    compose: StoryComposeState,
    caption: CaptionText,
    onEvent: (NewStoryEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth().padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                FormattingBar(caption)
                CaptionField(caption)
                AudienceRow(compose, onEvent)
                PostRow(state, compose, onEvent)
            }
        }
    }
}

/** The caption in the rounded tonal field, its formatting drawn as messages show it. */
@Composable
private fun CaptionField(caption: CaptionText) {
    TextField(
        state = caption.state,
        label = { Text(stringResource(R.string.new_story_caption)) },
        inputTransformation = caption.inputTransformation,
        outputTransformation = rememberFormattingTransformation(caption),
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.tonalColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What the post reaches and "Post", or why Telegram does not let the user post now. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostRow(
    state: NewStoryUiState,
    compose: StoryComposeState,
    onEvent: (NewStoryEvent) -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val blocked = blockedText(compose)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            blocked ?: summaryText(state, compose),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (blocked !=
                    null
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(
            onClick = { onEvent(NewStoryEvent.Post) },
            enabled = compose.canPost,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.heightIn(min = height),
        ) {
            if (compose.isPosting) {
                val posting = stringResource(R.string.new_story_posting)
                LoadingIndicator(
                    Modifier.size(ButtonDefaults.iconSizeFor(height)).semantics { contentDescription = posting },
                )
            } else {
                Icon(
                    painterResource(UiR.drawable.ic_send_filled),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
            }
            Text(
                stringResource(R.string.new_story_post),
                style = ButtonDefaults.textStyleFor(height),
                modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
            )
        }
    }
}

/** The media overlay's track: the segments of the items not shown. */
private const val TRACK_ALPHA = 0.38f
