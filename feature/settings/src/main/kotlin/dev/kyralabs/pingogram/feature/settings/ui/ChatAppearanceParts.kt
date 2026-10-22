package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.brush
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the Chat appearance page (docs/DESIGN_SYSTEM.md §6, "Chat appearance"): the preview and the chips.

/**
 * A conversation as the theme's chat style ([chatStyle]) draws it: on the wallpaper (the conversation's `surface`
 * without one), two incoming messages of one sender and an outgoing reply, in the bubbles' shapes and colors
 * (`docs/DESIGN_SYSTEM.md` §2.2, §4) and the message text style. Static sample content; screen readers hear one
 * description.
 */
@Composable
internal fun ChatPreview(modifier: Modifier = Modifier) {
    val style = MaterialTheme.chatStyle
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.settings_chat_preview)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description }
                .background(style.wallpaper ?: SolidColor(colors.surface))
                .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        PreviewMessage(
            stringResource(R.string.settings_chat_sample_first),
            outgoing = false,
            first = true,
            last = false,
        )
        PreviewMessage(
            stringResource(R.string.settings_chat_sample_second),
            outgoing = false,
            first = false,
            last = true,
        )
        Box(Modifier.padding(top = MaterialTheme.spacing.small)) {
            PreviewMessage(
                stringResource(R.string.settings_chat_sample_reply),
                outgoing = true,
                first = true,
                last = true,
            )
        }
    }
}

/** One bubble of [ChatPreview], its corners joined to the messages of its group as in a conversation. */
@Composable
private fun PreviewMessage(
    text: String,
    outgoing: Boolean,
    first: Boolean,
    last: Boolean,
) {
    val style = MaterialTheme.chatStyle
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.bubbleMaxWidth),
            shape = style.bubbleShapes.of(outgoing, joinsPrevious = !first, joinsNext = !last),
            color = if (outgoing) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (outgoing) colors.onPrimaryContainer else colors.onSurface,
        ) {
            Text(
                text = text,
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    ),
                // Sample messages are what people write: they take their own direction, as in the conversation.
                style = style.messageText.userContent,
            )
        }
    }
}

/**
 * A wallpaper's chip, as the Theme page's ([ThemeChip]): a circle of `sizes.themeChip` filled with [wallpaper] as the
 * theme draws it (the conversation's `surface` with an outline for none). While [selected] it morphs into
 * `MaterialShapes.Cookie9Sided` on the default spatial spring (snapping with reduced motion), with a check in
 * `primaryContainer`. Screen readers hear a radio button named after the wallpaper; a pick ticks (`SegmentTick`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WallpaperChip(
    wallpaper: ChatWallpaper,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = selected)
    val haptics = LocalHapticFeedback.current
    val name = wallpaper.label()
    Box(
        modifier =
            modifier
                .size(MaterialTheme.sizes.themeChip)
                .selectable(selected = selected, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics { contentDescription = name }
                .clip(shape)
                .background(wallpaper.brush(colors) ?: SolidColor(colors.surfaceContainerHighest)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(MaterialTheme.spacing.extraSmall)
                            .size(IconButtonDefaults.smallIconSize),
                )
            }
        } else if (wallpaper == ChatWallpaper.None) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** What screen readers and the page call [this] wallpaper. */
@Composable
internal fun ChatWallpaper.label(): String =
    stringResource(
        when (this) {
            ChatWallpaper.None -> R.string.settings_chat_wallpaper_none
            ChatWallpaper.Neutral -> R.string.settings_chat_wallpaper_neutral
            ChatWallpaper.Primary -> R.string.settings_chat_wallpaper_primary
            ChatWallpaper.Secondary -> R.string.settings_chat_wallpaper_secondary
            ChatWallpaper.Tertiary -> R.string.settings_chat_wallpaper_tertiary
            ChatWallpaper.PrimaryToTertiary -> R.string.settings_chat_wallpaper_primary_tertiary
            ChatWallpaper.SecondaryToPrimary -> R.string.settings_chat_wallpaper_secondary_primary
            ChatWallpaper.TertiaryToSecondary -> R.string.settings_chat_wallpaper_tertiary_secondary
            ChatWallpaper.NeutralToPrimary -> R.string.settings_chat_wallpaper_neutral_primary
        },
    )

/** How round [this] makes bubbles, in words. */
@Composable
internal fun BubbleCorners.label(): String =
    stringResource(
        when (this) {
            BubbleCorners.Medium -> R.string.settings_chat_corners_medium
            BubbleCorners.Large -> R.string.settings_chat_corners_large
            BubbleCorners.LargeIncreased -> R.string.settings_chat_corners_large_increased
            BubbleCorners.ExtraLarge -> R.string.settings_chat_corners_extra_large
            BubbleCorners.ExtraLargeIncreased -> R.string.settings_chat_corners_extra_large_increased
        },
    )
