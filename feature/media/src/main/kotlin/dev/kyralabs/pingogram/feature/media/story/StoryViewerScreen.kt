package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.PingogramDarkTheme
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.DismissState
import dev.kyralabs.pingogram.feature.media.ui.ImmersiveSystemBars
import dev.kyralabs.pingogram.feature.media.ui.PredictiveBackDismiss
import dev.kyralabs.pingogram.feature.media.ui.followingDismiss
import dev.kyralabs.pingogram.feature.media.ui.layoutFollowingDismiss
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import dev.kyralabs.pingogram.feature.media.ui.rememberDismissState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The full-screen stories viewer (`docs/ROADMAP.md` M6; `docs/DESIGN_SYSTEM.md` §5–6, Story viewer, §9): edge to edge
 * on the theme's `scrim`, in the dark theme of the app's colors ([PingogramDarkTheme]), as immersive media is, with
 * light system bar icons ([ImmersiveSystemBars]).
 *
 * - **Chats**: a [HorizontalPager] of [StoryViewerUiState.Content.posters], keyed by chat, a plain slide on the
 *   pager's own fling; the page it settles on goes to the view model ([StoryViewerEvent.PosterSettled]), and the view
 *   model's next chat (after the last story of one) scrolls the pager there on the default spatial spring. While the
 *   pager moves, the story pauses. Each page is a [StoryPage]; pages beside the current one show their chat only.
 * - **Leaving**: predictive back ([PredictiveBackDismiss]) shrinks the story towards 90% and fades the background with
 *   the gesture; a swipe down (or up) moves it with the finger, closing past a threshold or with a fling, else
 *   springing back ([DismissState]), the story paused meanwhile. Both call [onClose], once.
 * - **Keyboard** ([storyKeys], `docs/DESIGN_SYSTEM.md` §9): Left and Right show the previous and next story
 *   (mirrored in right-to-left layouts), Space pauses and resumes, Esc closes the viewer.
 * - **States**: a [DelayedLoadingIndicator] while the chats are not known.
 *
 * @param videoSurface draws the player's video in the current story; the route passes Media3's `PlayerSurface`,
 *   previews and tests nothing.
 */
@Composable
internal fun StoryViewerScreen(
    state: StoryViewerUiState,
    onEvent: (StoryViewerEvent) -> Unit,
    onClose: () -> Unit,
    replyText: TextFieldState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    videoSurface: @Composable (Modifier) -> Unit = {},
) {
    PingogramDarkTheme {
        val dismiss = rememberDismissState()
        // Every way out closes the viewer once, however quickly the next one follows (DismissState.dismiss).
        val leave = remember(dismiss, onClose) { { dismiss.dismiss(onClose) } }
        PredictiveBackDismiss(dismiss, onClose)
        ImmersiveSystemBars(hidden = false)
        val scrim = MaterialTheme.colorScheme.scrim
        val title = LocalResources.current.getString(R.string.story_viewer_title)
        BoxWithConstraints(modifier.fillMaxSize().semantics { paneTitle = title }) {
            val height = constraints.maxHeight.toFloat()
            Box(Modifier.matchParentSize().drawBehind { drawRect(scrim, alpha = 1f - dismiss.fraction(height)) })
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // The stories take the insets themselves (StoryPage).
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                // No bars: the padding is nothing, but the snackbars' room once one shows.
                val keys = Modifier.storyKeys((state as? StoryViewerUiState.Content)?.isPaused == true, onEvent, leave)
                DismissibleStories(dismiss, height, onClose, onEvent, Modifier.padding(innerPadding).then(keys)) {
                    when (state) {
                        StoryViewerUiState.Loading -> {
                            val loading = LocalResources.current.getString(R.string.story_viewer_loading)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                            }
                        }

                        is StoryViewerUiState.Content -> {
                            StoryPager(state, onEvent, leave, replyText, videoSurface)
                        }
                    }
                }
            }
        }
    }
}

/**
 * [content] following [dismiss]: shrunk by a predictive back gesture, and moved by a vertical drag, which closes the
 * viewer ([onClose]) past a threshold of the page's [height] or with a fling, else springs back on the motion scheme's
 * default spatial spring. The story pauses while dragged ([StoryViewerEvent.Hold]).
 */
@Composable
private fun DismissibleStories(
    dismiss: DismissState,
    height: Float,
    onClose: () -> Unit,
    onEvent: (StoryViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .followingDismiss(dismiss)
                .layoutFollowingDismiss(dismiss)
                .draggable(
                    state = dismiss.draggableState,
                    orientation = Orientation.Vertical,
                    enabled = !dismiss.isDismissed,
                    onDragStarted = {
                        onEvent(StoryViewerEvent.Hold(true))
                        dismiss.onDragStarted()
                    },
                    onDragStopped = { velocity ->
                        dismiss.onDragStopped(velocity, height, settle, scope, onDismiss = onClose)
                        onEvent(StoryViewerEvent.Hold(false))
                    },
                ),
    ) { content() }
}

/**
 * The chats' pages, the current one ([StoryViewerUiState.Content.current]) showing its story. The pager follows the
 * view model's current chat, and reports where the user swiped and whether it moves.
 */
@Composable
private fun StoryPager(
    state: StoryViewerUiState.Content,
    onEvent: (StoryViewerEvent) -> Unit,
    onClose: () -> Unit,
    replyText: TextFieldState,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    val posters = state.posters
    val pagerState = rememberPagerState(initialPage = state.current) { posters.size }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { currentOnEvent(StoryViewerEvent.Paging(it)) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { currentOnEvent(StoryViewerEvent.PosterSettled(it)) }
    }
    LaunchedEffect(state.current) {
        if (pagerState.settledPage != state.current) pagerState.animateScrollToPage(state.current, animationSpec = spec)
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { posters[it].chatId },
    ) { page ->
        val poster = posters[page]
        if (page == state.current) {
            StoryPage(
                poster = poster,
                state = state,
                onEvent = onEvent,
                onClose = onClose,
                replyText = replyText,
                videoSurface = videoSurface,
            )
        } else {
            StoryPlaceholderPage(poster = poster, onClose = onClose)
        }
    }
}

@PingogramPreviews
@Composable
private fun StoryViewerScreenPreview(
    @PreviewParameter(StoryViewerPreviewStates::class) state: StoryViewerUiState,
) {
    PingogramPreview {
        PreviewImages { StoryViewerScreen(state = state, onEvent = {}, onClose = {}, replyText = TextFieldState()) }
    }
}
