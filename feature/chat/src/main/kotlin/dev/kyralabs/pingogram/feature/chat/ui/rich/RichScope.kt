package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.LinkInteractionListener
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.feature.chat.ui.BubbleResources
import dev.kyralabs.pingogram.feature.chat.ui.BubbleStyle
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.MediaDownloads

/**
 * What a rich message's blocks share as they draw: the message's [texts], the bubble's [style], how the user changed
 * it ([ui]), how its photos download ([downloads]) and where its taps go ([onEvent]).
 */
@Stable
internal class RichScope(
    val messageId: Long,
    val texts: RichTexts,
    val style: BubbleStyle,
    val ui: RichMessageUi?,
    val downloads: MediaDownloads,
    val onEvent: (ChatEvent) -> Unit,
) {
    val colors: BubbleColors get() = style.colors
}

/**
 * The [RichScope] of [bubble]'s [message]: its texts from the list's cache ([RichTextCache]), with spoilers that
 * reveal the bubble's ([ChatEvent.RevealSpoilers]) and the list's link and mention listeners.
 */
@Composable
internal fun rememberRichScope(
    bubble: ChatItem.Bubble,
    message: RichMessage,
    style: BubbleStyle,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
): RichScope {
    val messageId = bubble.id
    val onSpoilerClick =
        remember(messageId, onEvent) { LinkInteractionListener { onEvent(ChatEvent.RevealSpoilers(messageId)) } }
    val texts =
        resources.texts.rich.get(
            messageId,
            message,
            style.text,
            bubble.spoilersRevealed,
            onSpoilerClick,
            resources.onLinkClick,
            resources.onMentionClick,
        )
    val ui = bubble.rich
    return remember(messageId, texts, style, ui, resources.mediaDownloads, onEvent) {
        RichScope(messageId, texts, style, ui, resources.mediaDownloads, onEvent)
    }
}
