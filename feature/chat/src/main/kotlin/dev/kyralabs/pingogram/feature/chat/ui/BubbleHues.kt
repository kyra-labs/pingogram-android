package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The experimental look's group bubbles (`docs/DESIGN_SYSTEM.md` §13.6): names and reply stripes in the sender's hue,
// role labels, and the name above a vivid bubble.

/**
 * The list's bubble theme while it has sender hues (the experimental look, §13.6), for the parts of a bubble that take
 * them; null otherwise. The same instance for every bubble of the list, so providing it around each bubble invalidates
 * nothing as rows are reused while scrolling.
 */
internal val LocalHueTheme = compositionLocalOf<BubbleTheme?> { null }

/** Whether [colors] are one of the sender hues' vivid bubbles (§13.6), whose name goes above them. */
internal fun BubbleTheme?.isVivid(colors: BubbleColors): Boolean =
    this != null && hues.any { it.vivid.colors === colors }

/**
 * The color of a sender's name or reply quote: on a vivid bubble ([colors]) its text color, readable on the fill, else
 * the sender [hue]'s ink in the experimental look, else the standard look's role by the sender's [id].
 */
@Composable
internal fun hueNameColor(
    hue: Int,
    id: Long?,
    colors: BubbleColors,
): Color {
    val theme = LocalHueTheme.current
    val ink = theme?.inkOf(hue)
    return when {
        ink != null && theme.isVivid(colors) -> colors.content
        ink != null -> ink
        id != null -> MaterialTheme.colorScheme.senderNameColor(id)
        else -> MaterialTheme.colorScheme.primary
    }
}

/** The stripe at the start of a reply quote in the experimental look (§13.6): [width] wide in [color]. */
internal fun Modifier.quoteStripe(
    color: Color?,
    width: Dp,
): Modifier =
    if (color == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            val stripe = width.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - stripe else 0f
            drawRect(color, topLeft = Offset(x, 0f), size = Size(stripe, size.height))
        }
    }

/**
 * A sender's role ("Admin", "Owner", their tag) after their name (§13.6): a small pill in the [ink] of their hue, its
 * text in [content], which keeps 4.5:1 on it; decorative, as screen readers hear the role with the bubble.
 */
@Composable
internal fun RolePill(
    role: UiText,
    ink: Color,
    content: Color,
) {
    val spacing = MaterialTheme.spacing
    Text(
        text = role.resolve(),
        modifier =
            Modifier
                .clearAndSetSemantics {}
                .background(ink, CircleShape)
                .padding(horizontal = spacing.small),
        color = content,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmallEmphasized,
    )
}

/**
 * The name above a vivid bubble in the experimental look (§13.6): the sender's name in their hue's ink, then their
 * role; opening their profile as the name inside a bubble does.
 */
@Composable
internal fun SenderNameAbove(
    bubble: ChatItem.Bubble,
    theme: BubbleTheme,
    onEvent: (ChatEvent) -> Unit,
) {
    val hue = theme.hues[bubble.senderHue % theme.hues.size]
    Row(
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bubble.sender.resolve(),
            modifier = Modifier.openProfileTarget(bubble.senderUserId, MaterialTheme.shapes.extraSmall, onEvent),
            color = hue.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.userContent,
        )
        bubble.senderRole?.let { RolePill(it, hue.ink, hue.tinted.colors.container) }
    }
}

/** Whether [bubble]'s name goes above it: a vivid bubble's first of a group in the experimental look (§13.6). */
internal fun BubbleTheme.namesAbove(bubble: ChatItem.Bubble): Boolean =
    bubble.showsSenderName && vividHues && bubble.senderHue >= 0 && hues.isNotEmpty()

/**
 * The conversation's [BubbleResources] for [state]: its shortcuts and downloads, a tapped mention opening what it names
 * ([ChatEvent.OpenMention]), and in the experimental look vivid or tinted group bubbles, as Chat appearance's "Group
 * bubbles" and the group's size say (§13.6).
 */
@Composable
internal fun rememberChatBubbleResources(
    state: ChatUiState,
    jumpState: MessageJumpState,
    lift: MessageLiftState,
    onEvent: (ChatEvent) -> Unit,
): BubbleResources {
    val vivid = MaterialTheme.chatStyle.groupBubbles.isVivid(state.groupMemberCount)
    return rememberBubbleResources(jumpState, state.shortcuts, state.mediaDownloads, lift, vivid) { target ->
        onEvent(ChatEvent.OpenMention(target))
    }
}

/** How many saw a channel post: the visibility symbol and the count in the language's digits; decorative. */
@Composable
internal fun PostViews(
    views: Int,
    color: Color,
    style: TextStyle,
) {
    val locale = LocalConfiguration.current.locales[0]
    val count = remember(views, locale) { NumberFormat.getIntegerInstance(locale).format(views) }
    Icon(
        painter = painterResource(UiR.drawable.ic_visibility),
        contentDescription = null,
        modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        tint = color,
    )
    Text(count, color = color, maxLines = 1, style = style)
}

/**
 * A shouted word in the experimental look (`docs/DESIGN_SYSTEM.md` §13.6, [ChatItem.Bubble.isShout]): no bubble, the
 * word in `displayLarge` at the condensed width, italic, in its sender's ink ([BubbleTheme.inkOf]; `onSurface` without
 * a hue), the time and state on the surface below it as an emoji-only message has them.
 */
@Composable
internal fun ShoutBubble(
    bubble: ChatItem.Bubble,
    theme: BubbleTheme,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.Start,
    ) {
        BubbleHeader(bubble, colors, onEvent)
        Text(
            text = bubble.text.text,
            color = theme.inkOf(bubble.senderHue) ?: MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.experimentalTypography.displayLargeCondensed.copy(fontStyle = FontStyle.Italic),
        )
        if (bubble.showsTime) SurfaceMeta(bubble)
    }
}

/**
 * This text with its first [length] characters as a channel post's headline (§13.7): a paragraph of its own in
 * [style]'s font, size, weight and line height. The line break after it gives way to the paragraph's own end, which
 * would otherwise leave an empty line under the headline; the rest as it is.
 */
internal fun AnnotatedString.withHeadline(
    length: Int,
    style: TextStyle,
): AnnotatedString {
    if (length !in 1 until this.length - 1 || text[length] != '\n') return this
    val headline = subSequence(0, length)
    val rest = subSequence(length + 1, this.length)
    return buildAnnotatedString {
        withStyle(ParagraphStyle(lineHeight = style.lineHeight)) {
            withStyle(
                SpanStyle(
                    fontFamily = style.fontFamily,
                    fontSize = style.fontSize,
                    fontWeight = style.fontWeight,
                    letterSpacing = style.letterSpacing,
                ),
            ) { append(headline) }
        }
        append(rest)
    }
}
