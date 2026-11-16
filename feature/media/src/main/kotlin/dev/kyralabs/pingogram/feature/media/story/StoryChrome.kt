package dev.kyralabs.pingogram.feature.media.story

import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
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
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The top of the frame on a `mediaOverlay` gradient: the segments ([StorySegments]), then the chat's avatar
 * (`sizes.avatarSmall`), its name in `titleSmallEmphasized` and the story's age in `bodySmall` (one heading for screen
 * readers), and the mute button ([mute] is whether the video is muted; null without one), the ⋮ menu of the user's own
 * story ([menu], null for others) and the close button (`IconButton`s with `IconButtonDefaults.shapes()`), in
 * `onMediaOverlay`. A [live] story (board 26) has "LIVE" after the name, `labelSmallEmphasized` in `onError` on an
 * `error` pill, and its segment is a solid `error` bar rather than progress.
 */
@Suppress("LongParameterList") // The chat, its story's place and progress, its age, and the buttons.
@Composable
internal fun StoryTopChrome(
    poster: StoryPosterUi,
    index: Int,
    progress: () -> Float,
    ageSeconds: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    mute: Boolean? = null,
    onToggleMute: () -> Unit = {},
    menu: (@Composable () -> Unit)? = null,
    live: Boolean = false,
    held: Boolean = false,
) {
    val colors = MaterialTheme.pingogramColors
    val spacing = MaterialTheme.spacing
    // Held (board 26): the segments stay, dimmed, and the rest fades; read in the draw phase.
    val motion = MaterialTheme.motionScheme
    val heldSpec = if (held) motion.fastEffectsSpec<Float>() else motion.defaultEffectsSpec()
    val rest by animateFloatAsState(if (held) 0f else 1f, heldSpec, label = "heldChrome")
    val segments by animateFloatAsState(if (held) HELD_SEGMENTS_ALPHA else 1f, heldSpec, label = "heldSegments")
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(colors.mediaOverlay, Color.Transparent)), alpha = rest)
                }.padding(start = spacing.small, end = spacing.extraSmall, top = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        StorySegments(
            count = poster.storyCount.coerceAtLeast(index + 1),
            index = index,
            progress = progress,
            modifier = Modifier.padding(end = spacing.extraSmall).graphicsLayer { alpha = segments },
            live = live,
        )
        Row(Modifier.graphicsLayer { alpha = rest }, verticalAlignment = Alignment.CenterVertically) {
            Avatar(photo = poster.photo, name = poster.name, id = poster.chatId, size = MaterialTheme.sizes.avatarSmall)
            StoryTitle(poster.name, ageSeconds, live, Modifier.weight(1f).padding(horizontal = spacing.small))
            if (mute != null) {
                IconButton(onClick = onToggleMute, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter =
                            painterResource(
                                if (mute) R.drawable.ic_volume_off_filled else R.drawable.ic_volume_up_filled,
                            ),
                        contentDescription = stringResource(if (mute) R.string.story_unmute else R.string.story_mute),
                        tint = colors.onMediaOverlay,
                    )
                }
            }
            menu?.invoke()
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_close),
                    contentDescription = stringResource(R.string.story_close),
                    tint = colors.onMediaOverlay,
                )
            }
        }
    }
}

/**
 * The chat's name, "LIVE" after it for a [live] story, and the story's age ([ageSeconds], null to leave it out), one
 * heading for screen readers.
 */
@Composable
private fun StoryTitle(
    name: String,
    ageSeconds: Long?,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.pingogramColors.onMediaOverlay
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleSmallEmphasized.userContent,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (live) {
                Text(
                    text = stringResource(R.string.story_live_chip),
                    modifier =
                        Modifier
                            .padding(start = MaterialTheme.spacing.small)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .padding(horizontal = MaterialTheme.spacing.small),
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                )
            }
        }
        if (ageSeconds != null) {
            Text(text = storyAge(ageSeconds), style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
        }
    }
}

/**
 * One segment per story of the chat, `sizes.mediaAlbumGap` apart, `sizes.pinnedIndicator` tall with round ends: those
 * before [index] full, the current one filling with [progress], the others empty, in `onMediaOverlay` on its 38% track.
 * Drawn in the draw phase, so the progress never recomposes. Decorative: the story's own description says where it is.
 * A [live] story's segment is a solid `error` bar: it has no length to fill.
 */
@Composable
private fun StorySegments(
    count: Int,
    index: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val fill = MaterialTheme.pingogramColors.onMediaOverlay
    val liveFill = MaterialTheme.colorScheme.error
    val track = fill.copy(alpha = TRACK_ALPHA)
    val gap = MaterialTheme.sizes.mediaAlbumGap
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MaterialTheme.sizes.pinnedIndicator)
                .clearAndSetSemantics {}
                .drawBehind {
                    if (count <= 0) return@drawBehind
                    val gapPx = gap.toPx()
                    val width = (size.width - gapPx * (count - 1)) / count
                    val radius = CornerRadius(size.height / 2)
                    repeat(count) { segment ->
                        val start = segment * (width + gapPx)
                        val left = if (rtl) size.width - start - width else start
                        drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
                        val filled =
                            when {
                                segment < index -> 1f
                                segment == index -> if (live) 1f else progress()
                                else -> 0f
                            }
                        if (filled > 0f) {
                            val filledWidth = width * filled
                            val filledLeft = if (rtl) left + width - filledWidth else left
                            val color = if (live && segment == index) liveFill else fill
                            drawRoundRect(color, Offset(filledLeft, 0f), Size(filledWidth, size.height), radius)
                        }
                    }
                },
    )
}

/**
 * How long ago a story was posted, as Telegram's apps say it: "now" in its first minute, then minutes and hours in
 * the language's narrow units ("12m", "3h"), at most a day or two, as stories expire.
 */
@Composable
internal fun storyAge(ageSeconds: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val now = stringResource(R.string.story_age_now)
    return remember(ageSeconds, locale, now) {
        when {
            ageSeconds < SECONDS_PER_MINUTE -> now
            ageSeconds < SECONDS_PER_HOUR -> narrow(locale, ageSeconds / SECONDS_PER_MINUTE, MeasureUnit.MINUTE)
            else -> narrow(locale, ageSeconds / SECONDS_PER_HOUR, MeasureUnit.HOUR)
        }
    }
}

/** How long ago a story was posted, in words for screen readers: "now", "12 minutes ago", "2 hours ago". */
internal fun storyAgeSpoken(
    ageSeconds: Long,
    locale: Locale,
): String {
    val formatter = RelativeDateTimeFormatter.getInstance(locale)
    return when {
        ageSeconds < SECONDS_PER_MINUTE -> {
            formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        }

        ageSeconds < SECONDS_PER_HOUR -> {
            val minutes = (ageSeconds / SECONDS_PER_MINUTE).toDouble()
            formatter.format(
                minutes,
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.MINUTES,
            )
        }

        else -> {
            val hours = (ageSeconds / SECONDS_PER_HOUR).toDouble()
            formatter.format(
                hours,
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.HOURS,
            )
        }
    }
}

private fun narrow(
    locale: Locale,
    amount: Long,
    unit: MeasureUnit,
): String = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW).format(Measure(amount, unit))

/** The segments while the story is held (board 26: 60%). */
private const val HELD_SEGMENTS_ALPHA = 0.6f

/** The segments' track: Material's disabled opacity. */
private const val TRACK_ALPHA = 0.38f

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
