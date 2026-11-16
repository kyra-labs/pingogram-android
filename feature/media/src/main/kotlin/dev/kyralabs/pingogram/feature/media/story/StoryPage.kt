package dev.kyralabs.pingogram.feature.media.story

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.LocalVoiceChats
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.BlurredMinithumbnail
import dev.kyralabs.pingogram.feature.media.ui.ExpandableCaption
import dev.kyralabs.pingogram.feature.media.ui.ViewerError
import dev.kyralabs.pingogram.feature.media.ui.ViewerImage
import dev.kyralabs.pingogram.feature.media.ui.coveredBy
import dev.kyralabs.pingogram.feature.media.ui.rememberViewerImageAlpha
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The page of the current chat: its story in a 9:16 frame with `large` corners, as large as the window allows inside
 * the safe area ([StoryFrame]), and below it the reply field and the reactions ([StoryBottomBar]).
 *
 * - **Frame** (on `surfaceContainer` until the picture shows): the tap zones ([StoryTapZones]: the start third goes
 *   back, the rest forward, mirrored right to left; holding pauses) under the story's picture ([StoryMediaContent]),
 *   over them the segments and the chat's name, the story's age,
 *   the mute and close buttons on a `mediaOverlay` gradient at the top ([StoryTopChrome]), and the caption with its
 *   formatting on one at the bottom.
 * - **Back** closes the reactions first, while they are open.
 */
@Suppress("LongParameterList") // The chat and its state, the events, closing, the reply's text and the video surface.
@Composable
internal fun StoryPage(
    poster: StoryPosterUi,
    state: StoryViewerUiState.Content,
    onEvent: (StoryViewerEvent) -> Unit,
    onClose: () -> Unit,
    replyText: TextFieldState,
    videoSurface: @Composable (Modifier) -> Unit,
) {
    val story = state.story
    BackHandler(enabled = state.reactions?.isOpen == true) { onEvent(StoryViewerEvent.ReactionsOpen(false)) }
    StoryPageLayout(
        frame = {
            // Under the picture, which takes no input, so taps reach them; a failed story's "Try again" takes its own.
            StoryTapZones(poster, story, state.isPaused, canReact = state.reactions != null, onEvent)
            StoryMediaContent(
                story.media,
                onRetry = { onEvent(StoryViewerEvent.Retry) },
                videoSurface = videoSurface,
                posterName = poster.name,
                onSkip = { onEvent(StoryViewerEvent.Next) },
            )
            // Dimmed while the reactions show, so they stand out over the picture (board 26).
            HeldFade(visible = state.reactions?.isOpen == true, Modifier.matchParentSize()) { ReactionsScrim() }
            // Held, the chrome steps aside for the picture, the segments dimmed, and a badge says to let go (board 26).
            StoryTopChrome(
                held = state.isHeld,
                poster =
                    if (state.own !=
                        null
                    ) {
                        poster.copy(name = stringResource(R.string.story_yours))
                    } else {
                        poster
                    },
                index = story.index,
                progress = story.progress,
                ageSeconds = story.ageSeconds,
                onClose = onClose,
                mute = (story.media as? StoryMedia.Video)?.takeIf { it.hasSound }?.let { state.isMuted },
                onToggleMute = { onEvent(StoryViewerEvent.ToggleMute) },
                menu = state.own?.let { own -> { OwnStoryMenu(own, onEvent) } },
                live = story.media is StoryMedia.Live,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (story.caption.text.isNotEmpty()) {
                HeldFade(visible = !state.isHeld, Modifier.align(Alignment.BottomCenter)) { StoryCaption(story) }
            }
            HeldFade(visible = state.isHeld, Modifier.align(Alignment.Center)) { HeldBadge() }
        },
        bottom = {
            // Held, the bar fades with the chrome and keeps its room, so the frame does not move (board 26).
            val motion = MaterialTheme.motionScheme
            val barAlpha by animateFloatAsState(
                targetValue = if (state.isHeld) 0f else 1f,
                animationSpec = if (state.isHeld) motion.fastEffectsSpec() else motion.defaultEffectsSpec(),
                label = "heldBar",
            )
            Box(Modifier.graphicsLayer { alpha = barAlpha }) {
                StoryBottomBar(state.reply, state.reactions, replyText, onEvent, state.views, state.canShare)
            }
        },
    )
}

/**
 * The chat's page the pager is leaving, as it was when it was the current one: [story]'s picture, its segments where
 * they were and its name, still. No taps, playback or bar: what it showed goes on showing until the pager settles,
 * rather than turning into a placeholder as the slide starts.
 */
@Composable
internal fun StoryLeavingPage(
    poster: StoryPosterUi,
    story: StoryUi,
    onClose: () -> Unit,
) {
    StoryPageLayout(
        frame = {
            StoryMediaContent(story.media, onRetry = {}, videoSurface = {}, posterName = poster.name)
            StoryTopChrome(
                poster = poster,
                index = story.index,
                progress = story.progress,
                ageSeconds = story.ageSeconds,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        bottom = { Spacer(Modifier.fillMaxWidth().height(barMinHeight())) },
    )
}

/**
 * A chat's page beside the current one, while the pager moves: its frame on `surfaceContainer` with its segments and
 * name, so the next chat slides in as itself.
 */
@Composable
internal fun StoryPlaceholderPage(
    poster: StoryPosterUi,
    onClose: () -> Unit,
) {
    StoryPageLayout(
        frame = {
            StoryTopChrome(
                poster = poster,
                index = poster.shownIndex,
                progress = NO_PROGRESS,
                ageSeconds = null,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        bottom = { Spacer(Modifier.fillMaxWidth().height(barMinHeight())) },
    )
}

/**
 * The frame and [bottom] below it as one column as wide as the frame, centered inside the safe area (system bars,
 * cutout and keyboard): the frame as large as fits above the bar, so the bar's buttons line up with its edges.
 */
@Composable
private fun StoryPageLayout(
    frame: @Composable BoxScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val frameWidth =
            minOf(maxWidth - MaterialTheme.spacing.extraSmall * 2, (maxHeight - barMinHeight()) * STORY_ASPECT_RATIO)
        Column(Modifier.width(frameWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(STORY_ASPECT_RATIO)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                content = frame,
            )
            bottom()
        }
    }
}

/** A photo story decoded at [widthPx] × [heightPx] over its blurred minithumbnail, which it covers once shown. */
@Composable
private fun StoryPhoto(
    media: StoryMedia.Photo,
    widthPx: Int,
    heightPx: Int,
    indicator: @Composable () -> Unit,
) {
    val alpha = rememberViewerImageAlpha(media.fileId)
    BlurredMinithumbnail(media.minithumbnail, Modifier.fillMaxSize(), covered = remember(alpha) { coveredBy(alpha) })
    if (media.isReady) {
        ViewerImage(media.fileId, widthPx, heightPx, Modifier.fillMaxSize(), alpha = alpha)
    } else {
        indicator()
    }
}

/**
 * What the story shows, filling the frame: a photo (decoded at the frame's size) or a video, each over its blurred
 * minithumbnail and with [StoryLoading] until its file is on the device; for a video its thumbnail until
 * its first frame. A live story says so ([LiveStoryNotice], "Skip live story" being [onSkip]); what the viewer cannot
 * show says so as an [EmptyState]: unsupported content, a deleted story; a failure with "Try again" ([onRetry]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoryMediaContent(
    media: StoryMedia,
    onRetry: () -> Unit,
    videoSurface: @Composable (Modifier) -> Unit,
    posterName: String,
    onSkip: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val indicator = @Composable { StoryLoading() }
        when (media) {
            StoryMedia.Loading -> {
                indicator()
            }

            is StoryMedia.Photo -> {
                StoryPhoto(media, widthPx, heightPx, indicator)
            }

            is StoryMedia.Video -> {
                // Under the surface only until its first frame, which covers it.
                if (!media.showsFrame) BlurredMinithumbnail(media.minithumbnail, Modifier.fillMaxSize())
                if (media.isReady) videoSurface(Modifier.fillMaxSize())
                if (!media.showsFrame && media.thumbnailFileId != null) {
                    ViewerImage(media.thumbnailFileId, widthPx, heightPx, Modifier.fillMaxSize())
                }
                if (!media.isReady) indicator()
            }

            is StoryMedia.Live -> {
                LiveStoryNotice(media, posterName, onSkip)
            }

            StoryMedia.Unsupported -> {
                StoryNotice(
                    R.string.story_unsupported_title,
                    R.string.story_unsupported_body,
                    MaterialShapes.Cookie9Sided,
                    UiR.drawable.ic_web_stories_filled,
                )
            }

            StoryMedia.Unavailable -> {
                // Said once as it shows, the viewer moving on by itself (board 26).
                StoryNotice(
                    R.string.story_unavailable_title,
                    R.string.story_unavailable_body,
                    MaterialShapes.Cookie12Sided,
                    UiR.drawable.ic_web_stories_filled,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            is StoryMedia.Failed -> {
                ViewerError(media.error, onRetry)
            }
        }
    }
}

/** What a story that cannot be shown says instead: [title] and [body] under [illustration] with [icon]. */
@Composable
private fun StoryNotice(
    @StringRes title: Int,
    @StringRes body: Int,
    illustration: RoundedPolygon,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        modifier = modifier,
        illustration = illustration,
        icon = icon,
        title = stringResource(title),
        body = stringResource(body),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/**
 * Taps and holds on the story: a tap on the start third (the left in left-to-right layouts) shows the previous story,
 * elsewhere the next; holding pauses until released ([StoryViewerEvent.Hold]). For screen readers one node, a polite
 * live region, so each story is announced as it shows ("Story 2 of 5 by Ada"), whose click is the next story and whose
 * custom actions are the previous and next story and pausing.
 */
@Composable
private fun StoryTapZones(
    poster: StoryPosterUi,
    story: StoryUi,
    isPaused: Boolean,
    canReact: Boolean,
    onEvent: (StoryViewerEvent) -> Unit,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val layoutDirection = LocalLayoutDirection.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(layoutDirection) {
                    detectTapGestures(
                        onPress = {
                            currentOnEvent(StoryViewerEvent.Hold(true))
                            tryAwaitRelease()
                            currentOnEvent(StoryViewerEvent.Hold(false))
                        },
                        onTap = { offset ->
                            val fromStart =
                                if (layoutDirection == LayoutDirection.Ltr) offset.x else size.width - offset.x
                            val previous = fromStart < size.width * PREVIOUS_ZONE_FRACTION
                            currentOnEvent(if (previous) StoryViewerEvent.Previous else StoryViewerEvent.Next)
                        },
                    )
                }.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = storyDescription(resources, locale, poster, story)
                    onClick(label = resources.getString(R.string.story_next)) {
                        currentOnEvent(StoryViewerEvent.Next)
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(resources.getString(R.string.story_previous)) {
                                currentOnEvent(StoryViewerEvent.Previous)
                                true
                            },
                            CustomAccessibilityAction(
                                resources.getString(if (isPaused) R.string.story_resume else R.string.story_pause),
                            ) {
                                currentOnEvent(StoryViewerEvent.Hold(!isPaused))
                                true
                            },
                        ) +
                        listOfNotNull(
                            CustomAccessibilityAction(resources.getString(R.string.story_react)) {
                                currentOnEvent(StoryViewerEvent.ReactionsOpen(true))
                                true
                            }.takeIf { canReact },
                        )
                },
    )
}

/** The caption at the bottom of the frame on a `mediaOverlay` gradient, with its formatting, 3 lines until tapped. */
@Composable
private fun StoryCaption(
    story: StoryUi,
    modifier: Modifier = Modifier,
) {
    val overlay = MaterialTheme.pingogramColors.mediaOverlay
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, overlay)))
                .padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.extraLarge,
                    bottom = MaterialTheme.spacing.large,
                ),
    ) {
        // Four lines at 200% font (board 26), three otherwise, then "Show more".
        val lines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) LARGE_CAPTION_LINES else CAPTION_LINES
        ExpandableCaption(caption = story.caption, key = story.storyId.toLong(), collapsedLines = lines)
    }
}

private const val CAPTION_LINES = 3
private const val LARGE_CAPTION_LINES = 4
private const val LARGE_FONT_SCALE = 2f

/** Stories are portrait, 9:16, as Telegram's apps record them. */
private const val STORY_ASPECT_RATIO = 9f / 16f

/** The share of the story's width at its start whose tap shows the previous story, as in Telegram's apps. */
private const val PREVIOUS_ZONE_FRACTION = 1f / 3f

private val NO_PROGRESS: () -> Float = { 0f }
