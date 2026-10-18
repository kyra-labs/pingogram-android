package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.PingogramDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaViewerPreviewStates
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import dev.kyralabs.pingogram.feature.media.ui.zoom.ZoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The full-screen media viewer (`REQUIREMENTS.md` FR-C10; `docs/DESIGN_SYSTEM.md` §5–6, §9): a full-bleed [Scaffold]
 * on the theme's `scrim`, edge to edge, in the dark theme of the app's colors ([PingogramDarkTheme]), as immersive
 * media is.
 *
 * - **Pages**: a [HorizontalPager] of [MediaViewerUiState.Content.items], keyed by message id, so loading older media
 *   in front keeps the page shown; newer media to the end. Each page is a [MediaPage]. The page the pager settles on
 *   goes to the view model ([MediaViewerEvent.PageSettled]). While the photo is zoomed in, the pager does not turn.
 * - **Chrome**: [ViewerTopBar] (back, sender and date, "3 of 120") and [ViewerBottomBar] (caption, a video's controls,
 *   and [bottomActions], the slot of the bottom app bar's actions), faded in and out on the motion scheme's effects
 *   springs when the media is tapped; hidden, the system bars hide too ([ImmersiveSystemBars]).
 * - **Leaving**: predictive back ([PredictiveBackDismiss]) shrinks the content towards 90% and fades the background
 *   with the gesture; a swipe down (or up) while not zoomed moves the content with the finger and fades the background,
 *   closing past a threshold or with a fling, else springing back on the default spatial spring ([DismissState]).
 *   Both call [onBack]. With animations off (reduced motion), the springs snap.
 * - **States**: a [LoadingIndicator] while the first media loads, an error with "Try again", and "This media is no
 *   longer available"; the top bar's back button in each.
 * - **Accessibility**: each page says what it is ("Photo, 3 of 120, Ada Lovelace, Oct 18, 9:41 AM, caption"), and
 *   offers turning to the older and newer page, zooming in and out, and showing a spoiler as custom actions; a tap
 *   (double tap with TalkBack) shows or hides the chrome.
 *
 * @param videoSurface draws the player's video in the page of the item in the player; the route passes Media3's
 *   `PlayerSurface`, previews and tests nothing.
 * @param bottomActions the actions of the bottom app bar (Share, Save), below the caption; none yet.
 */
@Composable
internal fun MediaViewerScreen(
    state: MediaViewerUiState,
    onEvent: (MediaViewerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    videoSurface: @Composable (Modifier) -> Unit = {},
    bottomActions: (@Composable () -> Unit)? = null,
) {
    PingogramDarkTheme {
        val content = state as? MediaViewerUiState.Content
        val chrome = rememberSaveable(saver = ChromeState.Saver) { ChromeState() }
        val dismiss = rememberDismissState()
        PredictiveBackDismiss(dismiss, onBack)
        ImmersiveSystemBars(hidden = content != null && !chrome.visible)
        val zoom = remember { ZoomState() }
        val scrim = MaterialTheme.colorScheme.scrim
        BoxWithConstraints(modifier.fillMaxSize()) {
            val height = constraints.maxHeight.toFloat()
            Box(Modifier.matchParentSize().drawBehind { drawRect(scrim, alpha = 1f - dismiss.fraction(height)) })
            Scaffold(
                topBar = {
                    Chrome(visible = chrome.visible || content == null, dismiss, height) {
                        ViewerTopBar(content?.current, content?.let { it.count?.positionOf(it.currentIndex) }, onBack)
                    }
                },
                bottomBar = {
                    if (content != null) {
                        Chrome(visible = chrome.visible, dismiss, height) {
                            ViewerBottomBar(content.current, content.playback, onEvent, actions = bottomActions)
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // The media is full bleed; the chrome takes its own insets.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                DismissibleContent(dismiss, zoom, height, onBack) {
                    ViewerContent(state, chrome, zoom, onEvent, videoSurface, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Whether the viewer's chrome shows; tapping the media toggles it. Saved, so it survives rotation. */
@Stable
internal class ChromeState(
    visible: Boolean = true,
) {
    var visible by mutableStateOf(visible)
        private set

    fun toggle() {
        visible = !visible
    }

    companion object {
        val Saver: Saver<ChromeState, Boolean> = Saver(save = { it.visible }, restore = { ChromeState(it) })
    }
}

/**
 * [content] following [dismiss]: shrunk by a predictive back gesture, and moved by a vertical drag, which closes the
 * viewer ([onBack]) past a threshold of the page's [height] or with a fling, else springs back on the motion scheme's
 * default spatial spring. No drag while the photo is zoomed in ([zoom]): it pans the photo then.
 */
@Composable
private fun DismissibleContent(
    dismiss: DismissState,
    zoom: ZoomState,
    height: Float,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val zoomed by remember(zoom) { derivedStateOf { zoom.isZoomed } }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .followingDismiss(dismiss)
                .layoutFollowingDismiss(dismiss)
                .draggable(
                    state = dismiss.draggableState,
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    onDragStarted = { dismiss.onDragStarted() },
                    onDragStopped = { velocity -> dismiss.onDragStopped(velocity, height, settle, scope, onBack) },
                ),
        content = content,
    )
}

/** The pages, or what shows instead of them. [modifier] keeps the states clear of the chrome; the pages ignore it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerContent(
    state: MediaViewerUiState,
    chrome: ChromeState,
    zoom: ZoomState,
    onEvent: (MediaViewerEvent) -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        MediaViewerUiState.Loading -> {
            val description = stringResource(R.string.media_loading)
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.semantics { contentDescription = description })
            }
        }

        is MediaViewerUiState.Error -> {
            ViewerError(state.error, onRetry = { onEvent(MediaViewerEvent.Retry) }, modifier)
        }

        MediaViewerUiState.Unavailable -> {
            ViewerUnavailable(modifier)
        }

        is MediaViewerUiState.Content -> {
            MediaPager(state, chrome, zoom, onEvent, videoSurface)
        }
    }
}

/**
 * The chrome's bars, shown while [visible], fading in on the default and out on the fast effects spring, and with
 * the background while a gesture closes the viewer (on a page [height] pixels tall).
 */
@Composable
private fun Chrome(
    visible: Boolean,
    dismiss: DismissState,
    height: Float,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.graphicsLayer { alpha = 1f - dismiss.fraction(height) },
        enter = fadeIn(motionScheme.defaultEffectsSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        content()
    }
}

/**
 * The pages of [content], starting on its current item and keyed by message id; the page settled on goes to [onEvent]
 * by its message id and resets [zoom]. Only the settled page of a shown photo zooms. A tap shows a spoiler, or else
 * shows or hides [chrome].
 */
@Composable
private fun MediaPager(
    content: MediaViewerUiState.Content,
    chrome: ChromeState,
    zoom: ZoomState,
    onEvent: (MediaViewerEvent) -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    val items = content.items
    val currentItems by rememberUpdatedState(items)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val zoomed by remember(zoom) { derivedStateOf { zoom.isZoomed } }
    // Not saved: after a rotation or process death the view model's current item is the page to start on.
    val pagerState = remember { PagerState(currentPage = content.currentIndex) { currentItems.size } }
    LaunchedEffect(pagerState, zoom) {
        var settledId: Long? = null
        snapshotFlow { currentItems.getOrNull(pagerState.settledPage)?.messageId }.collect { messageId ->
            // By id: older media loading in front moves the settled index, not the page.
            if (messageId != null && messageId != settledId) {
                settledId = messageId
                zoom.reset()
                currentOnEvent(MediaViewerEvent.PageSettled(messageId))
            }
        }
    }
    val scope = rememberCoroutineScope()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = MaterialTheme.spacing.large,
        userScrollEnabled = !zoomed,
        key = { index -> items[index].messageId },
    ) { page ->
        val item = items[page]
        val isShown = page == pagerState.settledPage
        val pageZoom = zoom.takeIf { isShown && item.media is ViewerMedia.Image && !item.isHidden }
        val onTap = if (item.isHidden) ({ onEvent(MediaViewerEvent.RevealSpoiler(item.messageId)) }) else chrome::toggle
        val info = PageInfo(item, page, items.size, content.count?.positionOf(page))
        MediaPage(
            item = item,
            files = content.files,
            playback = content.playback?.takeIf { it.messageId == item.messageId },
            onTap = onTap,
            videoSurface = videoSurface,
            modifier =
                Modifier.pageSemantics(
                    page = info,
                    chromeVisible = chrome.visible,
                    zoomed = isShown && zoomed,
                    actions =
                        pageActions(
                            info,
                            pageZoom,
                            isShown && zoomed,
                            scope,
                        ) { pagerState.animateScrollToPage(it) },
                    onTap = onTap,
                ),
            zoom = pageZoom,
        )
    }
}

/** Page [index] of [count], showing [item] at [position] among the chat's media (null while unknown). */
private class PageInfo(
    val item: MediaItem,
    val index: Int,
    val count: Int,
    val position: MediaPosition?,
)

/**
 * What screen readers get of a page: what it shows, whether it is [zoomed] in, a click that shows or hides the chrome
 * (or shows a spoiler) through [onTap], and its custom [actions].
 */
@Composable
private fun Modifier.pageSemantics(
    page: PageInfo,
    chromeVisible: Boolean,
    zoomed: Boolean,
    actions: List<CustomAccessibilityAction>,
    onTap: () -> Unit,
): Modifier {
    val description = pageDescription(page.item, page.position)
    val clickLabel =
        stringResource(
            when {
                page.item.isHidden -> R.string.media_show_spoiler
                chromeVisible -> R.string.media_hide_controls
                else -> R.string.media_show_controls
            },
        )
    val zoomedLabel = stringResource(R.string.media_zoomed_in)
    return semantics {
        contentDescription = description
        if (zoomed) stateDescription = zoomedLabel
        onClick(label = clickLabel) {
            onTap()
            true
        }
        customActions = actions
    }
}

/**
 * The custom actions of a page for screen readers: turn to the older and newer page ([turn], in [scope]), and zoom the
 * photo in and out with [zoom], on the motion scheme's default spatial spring; out only while [zoomed] in.
 */
@Composable
private fun pageActions(
    page: PageInfo,
    zoom: ZoomState?,
    zoomed: Boolean,
    scope: CoroutineScope,
    turn: suspend (page: Int) -> Unit,
): List<CustomAccessibilityAction> {
    val older = stringResource(R.string.media_show_older)
    val newer = stringResource(R.string.media_show_newer)
    val zoomIn = stringResource(R.string.media_zoom_in)
    val zoomOut = stringResource(R.string.media_zoom_out)
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    fun action(
        label: String,
        perform: () -> Unit,
    ) = CustomAccessibilityAction(label) {
        perform()
        true
    }
    return listOfNotNull(
        action(older) { scope.launch { turn(page.index - 1) } }.takeIf { page.index > 0 },
        action(newer) { scope.launch { turn(page.index + 1) } }.takeIf { page.index < page.count - 1 },
        zoom?.let { action(zoomIn) { it.step(ZOOM_STEP, spec, scope) } },
        zoom?.takeIf { zoomed }?.let { action(zoomOut) { it.step(1 / ZOOM_STEP, spec, scope) } },
    )
}

/** "Photo, 3 of 120, Ada Lovelace, Oct 18, 9:41 AM, caption": what the page shows, for screen readers. */
@Composable
private fun pageDescription(
    item: MediaItem,
    position: MediaPosition?,
): String {
    val kind =
        stringResource(
            when (item.media) {
                is ViewerMedia.Image -> UiR.string.preview_photo
                is ViewerMedia.Clip -> UiR.string.preview_video
                is ViewerMedia.Gif -> UiR.string.preview_gif
            },
        )
    val parts =
        listOfNotNull(
            kind,
            stringResource(R.string.media_spoiler).takeIf { item.isHidden },
            position?.let { positionText(it) },
            item.sender.resolve(),
            item.date,
            item.caption.text.takeIf { it.isNotEmpty() && !item.isHidden },
        )
    val resources = LocalResources.current
    return parts.reduce { joined, part -> resources.getString(R.string.media_description_part, joined, part) }
}

/** How much one "Zoom in" from an accessibility service zooms, and one "Zoom out" undoes. */
private const val ZOOM_STEP = 2f

@PingogramPreviews
@Composable
private fun MediaViewerScreenPreview(
    @PreviewParameter(MediaViewerPreviewStates::class) state: MediaViewerUiState,
) {
    PingogramPreview { PreviewImages { MediaViewerScreen(state = state, onEvent = {}, onBack = {}) } }
}
