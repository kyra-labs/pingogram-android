package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic colors that have no Material 3 role, derived from the active [ColorScheme] instead of
 * hard-coded (`docs/DESIGN_SYSTEM.md` §2.2).
 *
 * @property online presence dot for online users: Material green harmonized toward `primary`.
 * @property incomingBubble a message bubble of someone else and the content in it.
 * @property outgoingBubble a message bubble of the current user and the content in it.
 * @property mediaOverlay behind labels and badges drawn on photos and videos (a duration, a play badge): `scrim`,
 *   translucent, so the picture shows through while the label stays legible.
 * @property onMediaOverlay text and icons on [mediaOverlay]: a near-white role (`inverseOnSurface` in light theme,
 *   `onSurface` in dark theme).
 */
@Immutable
data class PingogramColors(
    val online: Color,
    val incomingBubble: BubbleColors,
    val outgoingBubble: BubbleColors,
    val mediaOverlay: Color,
    val onMediaOverlay: Color,
)

/**
 * A message bubble's roles (§2.2) and the colors of the content in it: voice messages, polls, files.
 *
 * Text in a bubble is in [content]. The accents are `primary` (and the harmonized green and `error` of quizzes)
 * wherever they keep 3:1 against [container]; where they do not, as on `primaryContainer` in the medium and high
 * contrast schemes, they fall back to [content], so every contrast level stays legible (§9).
 *
 * @property container the bubble: `surfaceContainerHigh` incoming, `primaryContainer` outgoing.
 * @property content text and icons on [container]: `onSurface` incoming, `onPrimaryContainer` outgoing.
 * @property accent play buttons, the played part of a voice message's waveform, poll bars, progress, the chosen
 *   answer of a poll, the circles behind content icons.
 * @property onAccent icons on [accent].
 * @property waveformUnplayed the part of a voice message's waveform that was not played yet: [content] at the opacity
 *   of disabled content, a track for [accent].
 * @property track the track behind a poll bar or a download's progress: [accent] at low opacity, a tonal tint of
 *   [container].
 * @property correct the mark of a quiz's correct answer: Material green harmonized toward `primary`, as [online].
 * @property wrong the mark of a wrong answer to a quiz: `error`.
 */
@Immutable
data class BubbleColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
    val waveformUnplayed: Color,
    val track: Color,
    val correct: Color,
    val wrong: Color,
)

internal fun pingogramColors(
    colorScheme: ColorScheme,
    dark: Boolean,
): PingogramColors {
    val green = harmonize(if (dark) OnlineDark else OnlineLight, colorScheme.primary)
    return PingogramColors(
        online = green,
        incomingBubble =
            bubbleColors(colorScheme, colorScheme.surfaceContainerHigh, colorScheme.onSurface, green),
        outgoingBubble =
            bubbleColors(colorScheme, colorScheme.primaryContainer, colorScheme.onPrimaryContainer, green),
        mediaOverlay = colorScheme.scrim.copy(alpha = MEDIA_OVERLAY_ALPHA),
        onMediaOverlay = if (dark) colorScheme.onSurface else colorScheme.inverseOnSurface,
    )
}

private fun bubbleColors(
    colorScheme: ColorScheme,
    container: Color,
    content: Color,
    green: Color,
): BubbleColors {
    val primaryIsReadable = colorScheme.primary.contrastWith(container) >= MIN_GRAPHICS_CONTRAST
    val accent = if (primaryIsReadable) colorScheme.primary else content
    return BubbleColors(
        container = container,
        content = content,
        accent = accent,
        onAccent = if (primaryIsReadable) colorScheme.onPrimary else container,
        waveformUnplayed = content.copy(alpha = DISABLED_CONTENT_ALPHA),
        track = accent.copy(alpha = TRACK_ALPHA),
        correct = green.readableOn(container, fallback = content),
        wrong = colorScheme.error.readableOn(container, fallback = content),
    )
}

private fun Color.readableOn(
    background: Color,
    fallback: Color,
): Color = if (contrastWith(background) >= MIN_GRAPHICS_CONTRAST) this else fallback

/** The WCAG contrast ratio of two opaque colors, from 1 to 21. */
internal fun Color.contrastWith(other: Color): Float {
    val lighter = maxOf(luminance(), other.luminance())
    val darker = minOf(luminance(), other.luminance())
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

// WCAG 2.1: icons, bars and other graphics need 3:1 against what is around them.
private const val MIN_GRAPHICS_CONTRAST = 3f
private const val CONTRAST_OFFSET = 0.05f

// Material's opacity of disabled content.
private const val DISABLED_CONTENT_ALPHA = 0.38f

// A tint that shows as a lighter bar on either bubble.
private const val TRACK_ALPHA = 0.16f

// Over a white photo, onMediaOverlay keeps at least 4.5:1 on this (about 5:1 in dark theme, 6:1 in light theme).
private const val MEDIA_OVERLAY_ALPHA = 0.64f

val LocalPingogramColors = staticCompositionLocalOf<PingogramColors> { error("Wrap the content in PingogramTheme") }

/** The [PingogramColors] provided by `PingogramTheme`. */
val MaterialTheme.pingogramColors: PingogramColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPingogramColors.current

/** Container and content colors of an avatar placeholder. */
@Immutable
data class AvatarColors(
    val container: Color,
    val content: Color,
)

/**
 * One of the seven avatar placeholder role pairs from §2.2, chosen by [id] (a TDLib user or chat id,
 * which can be negative) so the same chat always gets the same colors.
 */
fun ColorScheme.avatarPlaceholderColors(id: Long): AvatarColors =
    when (id.mod(AVATAR_COLOR_COUNT)) {
        0 -> AvatarColors(primaryContainer, onPrimaryContainer)
        1 -> AvatarColors(secondaryContainer, onSecondaryContainer)
        2 -> AvatarColors(tertiaryContainer, onTertiaryContainer)
        3 -> AvatarColors(primaryFixed, onPrimaryFixed)
        4 -> AvatarColors(secondaryFixed, onSecondaryFixed)
        5 -> AvatarColors(tertiaryFixed, onTertiaryFixed)
        else -> AvatarColors(surfaceVariant, onSurfaceVariant)
    }

private const val AVATAR_COLOR_COUNT = 7
