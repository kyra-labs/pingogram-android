package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The viewer's transparent small [TopAppBar] (`docs/DESIGN_SYSTEM.md` §6) over a gradient of `mediaOverlay` (the
 * theme's `scrim`, translucent) that fades into the media: back, the sender and the date as a two-line title, and
 * "3 of 120" ([position]) at the end. Before the media is there, [item] is null and only back shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerTopBar(
    item: MediaItem?,
    position: MediaPosition?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlay = MaterialTheme.pingogramColors.mediaOverlay
    TopAppBar(
        title = { if (item != null) ViewerTitle(item) },
        modifier = modifier.background(Brush.verticalGradient(listOf(overlay, Color.Transparent))),
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.media_back),
                )
            }
        },
        actions = {
            if (position != null) {
                Text(
                    text = positionText(position),
                    modifier = Modifier.padding(end = MaterialTheme.spacing.large),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        expandedHeight = titleHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/** The sender and the date, one heading for screen readers. */
@Composable
private fun ViewerTitle(item: MediaItem) {
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            text = item.sender.resolve(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = item.date,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The default height, or the height of the two lines of the title with some room when they need more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun titleHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines =
        with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp() }
    return max(TopAppBarDefaults.TopAppBarExpandedHeight, lines + MaterialTheme.spacing.large)
}

/** "3 of 120", in the digits of the current language. */
@Composable
internal fun positionText(position: MediaPosition): String =
    stringResource(R.string.media_position, position.number, position.total)

/**
 * The bottom of the viewer's chrome, over a gradient of `mediaOverlay` rising from the bottom: the current item's
 * caption ([ExpandableCaption]) and a video's controls ([VideoControls]) once it is in the player, at most
 * `contentMaxWidth` wide and centered on wide windows, and [actions] below them, centered: the slot of the floating
 * toolbar (`ViewerToolbar`). Nothing at all when there is nothing to show.
 */
@Composable
internal fun ViewerBottomBar(
    item: MediaItem,
    playback: VideoPlayback?,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val controls = playback?.takeIf { item.media is ViewerMedia.Clip && it.messageId == item.messageId }
    if (item.caption.text.isEmpty() && controls == null && actions == null) return
    val overlay = MaterialTheme.pingogramColors.mediaOverlay
    val maxWidth = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, overlay)))
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
                ).padding(top = MaterialTheme.spacing.extraLarge, bottom = FloatingToolbarDefaults.ScreenOffset),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (item.caption.text.isNotEmpty()) {
            ExpandableCaption(
                caption = item.caption,
                key = item.messageId,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.windowMarginCompact).then(maxWidth),
            )
        }
        val media = item.media
        if (controls != null && media is ViewerMedia.Clip) {
            VideoControls(
                playback = controls,
                durationSeconds = media.video.duration,
                onEvent = onEvent,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small).then(maxWidth),
            )
        }
        actions?.invoke()
    }
}

/**
 * The caption with its formatting (links open, spoilers show when tapped), at most [COLLAPSED_LINES] lines; tapping
 * it shows all of it, scrolling within about [EXPANDED_MAX_HEIGHT_FRACTION] of the window, and tapping again shows
 * less.
 * It grows and shrinks on the motion scheme's default spatial spring. Per item ([key]): another item starts collapsed.
 */
@Composable
private fun ExpandableCaption(
    caption: FormattedText,
    key: Long,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var spoilersRevealed by rememberSaveable(key) { mutableStateOf(false) }
    val styles = rememberFormattedTextStyles()
    val text =
        remember(caption, styles, spoilersRevealed) {
            caption.toAnnotatedString(styles, spoilersRevealed, onSpoilerClick = { spoilersRevealed = true })
        }
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val label = stringResource(if (expanded) R.string.media_caption_collapse else R.string.media_caption_expand)
    Text(
        text = text,
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .heightIn(max = windowHeight * EXPANDED_MAX_HEIGHT_FRACTION)
                .verticalScroll(rememberScrollState(), enabled = expanded)
                .clickable(onClickLabel = label) { expanded = !expanded },
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Play or pause and seek the current video (`REQUIREMENTS.md` FR-C10): a [FilledIconButton] whose shape morphs while
 * pressed, the time played, an expressive [Slider] and the length. The slider follows the playback in its layout and
 * draw phases ([SliderState], updated from [VideoPlayback.progress]), except while the user drags it; letting go seeks
 * there. The times recompose only when the second changes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoControls(
    playback: VideoPlayback,
    durationSeconds: Int,
    onEvent: (MediaViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sliderState = remember(playback.messageId) { SliderState(value = playback.progress()) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val touching by rememberUpdatedState(sliderState.isDragging || pressed)
    val progress = playback.progress
    LaunchedEffect(sliderState, progress) {
        snapshotFlow { progress() }.collect { fraction -> if (!touching) sliderState.value = fraction }
    }
    val played by remember(sliderState, durationSeconds) {
        derivedStateOf { (sliderState.value * durationSeconds).toInt() }
    }
    val locale = LocalConfiguration.current.locales[0]
    val playLabel = stringResource(if (playback.isPlaying) R.string.media_pause else R.string.media_play)
    val seekLabel = stringResource(R.string.media_seek)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { onEvent(MediaViewerEvent.PlayPause) }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter =
                    painterResource(
                        if (playback.isPlaying) UiR.drawable.ic_pause_filled else UiR.drawable.ic_play_arrow_filled,
                    ),
                contentDescription = playLabel,
            )
        }
        TimeText(formatDuration(played, locale))
        Slider(
            state = sliderState,
            onValueChange = { sliderState.value = it },
            modifier = Modifier.weight(1f).semantics { contentDescription = seekLabel },
            onValueChangeFinished = { onEvent(MediaViewerEvent.Seek(sliderState.value)) },
            interactionSource = interactionSource,
        )
        TimeText(formatDuration(durationSeconds, locale))
    }
}

/** A time beside the slider: `labelSmall` with tabular figures, so it does not jitter as it counts. */
@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

/** A media length or position, "0:05", "1:05" or "1:01:05", in the digits of [locale]. Negative counts as 0. */
internal fun formatDuration(
    seconds: Int,
    locale: Locale,
): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val rest = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(locale, "%d:%02d", minutes, rest)
    }
}

/** Lines of a collapsed caption. */
private const val COLLAPSED_LINES = 3

/** The most of the window's height an expanded caption takes before it scrolls. */
private const val EXPANDED_MAX_HEIGHT_FRACTION = 0.4f

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = 3_600
