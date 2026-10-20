package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Semantic colors that have no Material 3 role, derived from the active [ColorScheme] instead of
 * hard-coded (`docs/DESIGN_SYSTEM.md` §2.2).
 *
 * @property online presence dot for online users: Material green harmonized toward `primary`.
 * @property incomingBubble a message bubble of someone else and the content in it.
 * @property outgoingBubble a message bubble of the current user and the content in it.
 * @property selectedBubble a selected message bubble of either side, in selection mode, and the content in it.
 * @property mediaOverlay behind labels and badges drawn on photos and videos (a duration, a play badge): `scrim`,
 *   translucent, so the picture shows through while the label stays legible.
 * @property onMediaOverlay text and icons on [mediaOverlay]: a near-white role (`inverseOnSurface` in light theme,
 *   `onSurface` in dark theme).
 * @property mediaColorFilter for photos, videos and GIFs in the conversation, a media-heavy list (§10): in dark theme
 *   it dims them to 90% of their brightness, so they do not glare among the dark surfaces; null in light theme. Not
 *   for stickers, emoji and avatars, which are artwork rather than photos, nor for the media viewer.
 */
@Immutable
data class PingogramColors(
    val online: Color,
    val incomingBubble: BubbleColors,
    val outgoingBubble: BubbleColors,
    val selectedBubble: BubbleColors,
    val mediaOverlay: Color,
    val onMediaOverlay: Color,
    val mediaColorFilter: ColorFilter?,
)

/**
 * A message bubble's roles (§2.2) and the colors of the content in it: text, reply quotes, voice messages, polls,
 * files. The one source of bubble colors, for the bubble frame and the content components alike.
 *
 * Text in a bubble is in [content]. The accents are `primary` (and the harmonized green and `error` of quizzes)
 * wherever they keep 3:1 against [container]; where they do not, as on `primaryContainer` in the medium and high
 * contrast schemes, they fall back to [content], so every contrast level stays legible (§9).
 *
 * @property container the bubble: `surfaceContainerHigh` incoming, `primaryContainer` outgoing, `secondaryContainer`
 *   selected.
 * @property content text and icons on [container]: `onSurface` incoming, `onPrimaryContainer` outgoing,
 *   `onSecondaryContainer` selected.
 * @property secondaryContent the time, "edited", "Forwarded from …": `onSurfaceVariant` incoming,
 *   `onPrimaryContainer` outgoing, `onSecondaryContainer` selected.
 * @property block reply quotes, link previews and reactions inside the bubble, a tone that stands out from
 *   [container]: `surfaceContainerHighest` incoming, `surfaceContainerLow` outgoing and selected.
 * @property blockContent the text in a [block]: `onSurfaceVariant`.
 * @property accent play buttons, the played part of a voice message's waveform, poll bars, progress, the chosen
 *   answer of a poll, the circles behind content icons.
 * @property onAccent icons on [accent].
 * @property waveformUnplayed the part of a voice message's waveform that was not played yet: [content] at the opacity
 *   of disabled content, a track for [accent].
 * @property track the track behind a poll bar or a download's progress: [accent] at low opacity, a tonal tint of
 *   [container].
 * @property spoiler a hidden spoiler in a text: [secondaryContent] as a tonal tint over [container], at the lowest
 *   opacity, from that of disabled content up, that keeps 3:1 against [container] (§9), so it shows as a soft block
 *   that still stands out.
 * @property correct the mark of a quiz's correct answer: Material green harmonized toward `primary`, as [online].
 * @property wrong the mark of a wrong answer to a quiz: `error`.
 */
@Immutable
data class BubbleColors(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val block: Color,
    val blockContent: Color,
    val accent: Color,
    val onAccent: Color,
    val waveformUnplayed: Color,
    val track: Color,
    val spoiler: Color,
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
            bubbleColors(
                colorScheme = colorScheme,
                roles =
                    BubbleRoles(
                        container = colorScheme.surfaceContainerHigh,
                        content = colorScheme.onSurface,
                        secondaryContent = colorScheme.onSurfaceVariant,
                        block = colorScheme.surfaceContainerHighest,
                    ),
                green = green,
            ),
        outgoingBubble =
            bubbleColors(
                colorScheme = colorScheme,
                roles =
                    BubbleRoles(
                        container = colorScheme.primaryContainer,
                        content = colorScheme.onPrimaryContainer,
                        secondaryContent = colorScheme.onPrimaryContainer,
                        block = colorScheme.surfaceContainerLow,
                    ),
                green = green,
            ),
        selectedBubble =
            bubbleColors(
                colorScheme = colorScheme,
                roles =
                    BubbleRoles(
                        container = colorScheme.secondaryContainer,
                        content = colorScheme.onSecondaryContainer,
                        secondaryContent = colorScheme.onSecondaryContainer,
                        block = colorScheme.surfaceContainerLow,
                    ),
                green = green,
            ),
        mediaOverlay = colorScheme.scrim.copy(alpha = MEDIA_OVERLAY_ALPHA),
        onMediaOverlay = if (dark) colorScheme.onSurface else colorScheme.inverseOnSurface,
        mediaColorFilter = if (dark) MediaDim else null,
    )
}

/** The roles of one side's bubble, from which [bubbleColors] derives the rest. */
private class BubbleRoles(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val block: Color,
)

private fun bubbleColors(
    colorScheme: ColorScheme,
    roles: BubbleRoles,
    green: Color,
): BubbleColors {
    val container = roles.container
    val content = roles.content
    val primaryIsReadable = colorScheme.primary.contrastWith(container) >= MIN_GRAPHICS_CONTRAST
    val accent = if (primaryIsReadable) colorScheme.primary else content
    return BubbleColors(
        container = container,
        content = content,
        secondaryContent = roles.secondaryContent,
        block = roles.block,
        blockContent = colorScheme.onSurfaceVariant,
        accent = accent,
        onAccent = if (primaryIsReadable) colorScheme.onPrimary else container,
        waveformUnplayed = content.copy(alpha = DISABLED_CONTENT_ALPHA),
        track = accent.copy(alpha = TRACK_ALPHA),
        spoiler = roles.secondaryContent.tintOver(container),
        correct = green.readableOn(container, fallback = content),
        wrong = colorScheme.error.readableOn(container, fallback = content),
    )
}

/**
 * This color over [background] at the lowest opacity, from [DISABLED_CONTENT_ALPHA] up in [TINT_ALPHA_STEP]s, that
 * keeps 3:1 against it; this color itself if none does.
 */
private fun Color.tintOver(background: Color): Color {
    var alpha = DISABLED_CONTENT_ALPHA
    while (alpha < 1f) {
        val tint = copy(alpha = alpha).compositeOver(background)
        if (tint.contrastWith(background) >= MIN_GRAPHICS_CONTRAST) return tint
        alpha += TINT_ALPHA_STEP
    }
    return this
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

// How much more opaque each try of tintOver gets.
private const val TINT_ALPHA_STEP = 0.06f

// Dark theme dims photos and videos in lists to this brightness (docs/DESIGN_SYSTEM.md §10).
private const val MEDIA_DIM = 0.9f

private val MediaDim =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(MEDIA_DIM, MEDIA_DIM, MEDIA_DIM, 1f) })

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
 * One of the four avatar placeholder role pairs from §2.2, chosen by [id] (a TDLib user or chat id,
 * which can be negative) so the same chat always gets the same colors: the primary, secondary and tertiary
 * containers and a neutral pair. Container roles only: the `*Fixed` roles keep one light tone in dark theme, and
 * Material says not to use them where contrast matters.
 */
fun ColorScheme.avatarPlaceholderColors(id: Long): AvatarColors =
    when (id.mod(AVATAR_COLOR_COUNT)) {
        0 -> AvatarColors(primaryContainer, onPrimaryContainer)
        1 -> AvatarColors(secondaryContainer, onSecondaryContainer)
        2 -> AvatarColors(tertiaryContainer, onTertiaryContainer)
        else -> AvatarColors(surfaceContainerHighest, onSurfaceVariant)
    }

/**
 * The avatar placeholder colors on a selected or open row, whose `secondaryContainer` would swallow a container-colored
 * placeholder (§2.2): `primary` and `onPrimary`.
 */
fun ColorScheme.avatarPlaceholderColorsOnSelection(): AvatarColors = AvatarColors(primary, onPrimary)

private const val AVATAR_COLOR_COUNT = 4

/**
 * The color of a sender's name in a group conversation and in reply quotes, chosen by the sender's [id] (a TDLib user
 * or chat id, which can be negative) so a sender keeps one color: `primary`, `secondary` or `tertiary`. Roles of one
 * scheme, so they are harmonized with each other and readable on every bubble container (§2.2), unlike Telegram's
 * fixed palette of name colors.
 */
fun ColorScheme.senderNameColor(id: Long): Color =
    when (id.mod(SENDER_COLOR_COUNT)) {
        0 -> primary
        1 -> secondary
        else -> tertiary
    }

private const val SENDER_COLOR_COUNT = 3
