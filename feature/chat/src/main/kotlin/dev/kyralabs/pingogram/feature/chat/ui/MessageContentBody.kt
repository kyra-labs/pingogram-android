package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve

/**
 * What a bubble shows of its message's [content] above the text or caption (`REQUIREMENTS.md` FR-C4): the one place
 * that dispatches on the kind of content, so the media components (photos, albums, videos, stickers, voice notes,
 * documents, polls, locations, contacts, …, in `ui.content`) replace the placeholder case by case.
 *
 * For now every kind but text shows [label], the kind in one line ("Photo", "Voice message"), in the bubble's
 * secondary content color. Text messages show nothing here: the bubble shows their text and link preview itself, as
 * it does the caption of any media. Service messages never come here; they are pills, not bubbles.
 *
 * @param label what kind of message it is ([ChatItem.Bubble.contentLabel]); null for text messages.
 * @param colors the colors of the bubble's side, for media that draws on the bubble.
 */
@Composable
internal fun MessageContentBody(
    content: MessageContent,
    label: UiText?,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    when (content) {
        is MessageContent.Text, is MessageContent.Service -> Unit
        else -> label?.let { ContentPlaceholder(it, colors, modifier) }
    }
}

/** The kind of a message's content in one line, until its media component shows it. */
@Composable
private fun ContentPlaceholder(
    label: UiText,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label.resolve(),
        modifier = modifier,
        color = colors.secondaryContent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}
