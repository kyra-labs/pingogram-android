package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.WallpaperColorsTheme
import dev.kyralabs.pingogram.core.ui.theme.brush
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.wallpaper.WallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberWallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.wallpaperFrame
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChatPreview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The wallpaper page's live preview (ADR 0041): a conversation shaped like a phone's screen, on the wallpaper the page
 * shows (the pick, else what is set, over the account's theme-color one), with its top bar (the chat's photo and name,
 * or a sample's), a date chip, the sample messages and the composer pill, in the wallpaper's colors
 * ([WallpaperColorsTheme] of [WallpaperPageUiState.seed], its scheme made on [colorsDispatcher]). No status bar. Screen
 * readers hear one description; the names and messages are samples, user content.
 */
@Composable
internal fun WallpaperPagePreview(
    state: WallpaperPageUiState,
    modifier: Modifier = Modifier,
    colorsDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    WallpaperColorsTheme(state.seed, colorsDispatcher) { PreviewContent(state, modifier) }
}

@Composable
private fun PreviewContent(
    state: WallpaperPageUiState,
    modifier: Modifier,
) {
    val description = stringResource(R.string.settings_chat_preview)
    val frame = rememberWallpaperFrame()
    val backdrop = rememberPreviewBackdrop(state, frame)
    // Where the bar ends, set as it is placed and read only while drawing: the wallpaper is drawn sharp below it only.
    val barBottom = remember { mutableFloatStateOf(0f) }
    Box(
        modifier =
            modifier
                .previewFrame(MaterialTheme.sizes.mediaMaxWidth)
                .clip(MaterialTheme.shapes.extraLarge)
                .wallpaperFrame(frame)
                .clearAndSetSemantics { contentDescription = description },
        propagateMinConstraints = true,
    ) {
        val below = if (backdrop != null) Modifier.drawBelow(barBottom) else Modifier
        PreviewBackground(state, Modifier.matchParentSize().then(below), frame)
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            PreviewTopBar(state, backdrop, Modifier.onPlaced { barBottom.floatValue = it.size.height.toFloat() })
            Column(
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DateChip()
                ChatPreview(drawsWallpaper = false)
                ComposerPill()
            }
        }
    }
}

/**
 * What the preview draws behind the messages, in [frame]: a chat's own wallpaper, picked or set (the account's theme
 * color without one); for the account, the theme-color wallpaper shown, with the picked or set wallpaper over it.
 */
@Composable
private fun PreviewBackground(
    state: WallpaperPageUiState,
    modifier: Modifier,
    frame: WallpaperFrame,
) {
    val pending = state.picker.pending
    if (state.isChat) {
        PreviewWallpaper(pending, state.shownWallpaper, state.dimming, modifier, frame)
    } else {
        val colors = MaterialTheme.colorScheme
        Spacer(modifier.background(state.shownThemeColor.brush(colors) ?: SolidColor(colors.surface)))
        if (pending != null || state.shownWallpaper != null) {
            PreviewWallpaper(pending, state.shownWallpaper, state.dimming, modifier, frame)
        }
    }
}

/** Draws only below [top] (pixels from this box's top), clipped in the draw phase. */
private fun Modifier.drawBelow(top: FloatState): Modifier =
    drawWithContent { clipRect(top = top.floatValue) { this@drawWithContent.drawContent() } }

/** "Today" on an opaque pill, as the conversation's date chips. */
@Composable
private fun DateChip() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = stringResource(R.string.settings_wallpaper_preview_date),
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.extraSmall,
                ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** The floating composer: an emoji symbol and "Message" on a pill, beside the round voice button. */
@Composable
private fun ComposerPill() {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Surface(modifier = Modifier.weight(1f), shape = CircleShape, color = colors.surfaceContainerHigh) {
            Row(
                modifier = Modifier.padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Icon(painterResource(UiR.drawable.ic_mood), contentDescription = null, tint = colors.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.settings_wallpaper_preview_composer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Icon(
                painter = painterResource(UiR.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.padding(spacing.medium),
            )
        }
    }
}

/**
 * Sizes the preview like a phone's screen: [PREVIEW_WIDTH] of the page's width up to [maxWidth], and at least as
 * tall as [PREVIEW_ASPECT_RATIO] makes it; taller where large text needs it, so nothing is cut.
 */
private fun Modifier.previewFrame(maxWidth: Dp): Modifier =
    layout { measurable, constraints ->
        val width = minOf((constraints.maxWidth * PREVIEW_WIDTH).roundToInt(), maxWidth.roundToPx())
        val minHeight = (width / PREVIEW_ASPECT_RATIO).roundToInt()
        val maxHeight =
            if (constraints.hasBoundedHeight) {
                maxOf(
                    constraints.maxHeight,
                    minHeight,
                )
            } else {
                Constraints.Infinity
            }
        val placeable = measurable.measure(Constraints(width, width, minHeight, maxHeight))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** The preview's share of the page's width. */
private const val PREVIEW_WIDTH = 0.64f

/** A phone screen's width to its height, 9:19.5. */
private const val PREVIEW_ASPECT_RATIO = 9f / 19.5f

@PingogramPreviews
@Composable
private fun WallpaperPagePreviewPreview() {
    PingogramPreview {
        WallpaperPagePreview(WallpaperPageUiState(isChat = true, chat = WallpaperPageChat(1, "Meera", null)))
    }
}
