package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R

/**
 * What screen readers say for a bubble, which they read as one (`docs/DESIGN_SYSTEM.md` §9): "{sender}, {time}:
 * {content}, {status}", the status only for outgoing messages. The content is the forward's origin, the reply, the
 * kind of media, the text (hidden spoilers read "spoiler"), the link preview's title and the reactions. Built once
 * per bubble and language.
 */
@Composable
internal fun bubbleDescription(bubble: ChatItem.Bubble): String {
    val resources = LocalResources.current
    return remember(bubble, resources) { bubbleDescription(resources, bubble) }
}

internal fun bubbleDescription(
    resources: Resources,
    bubble: ChatItem.Bubble,
): String {
    val sender = bubble.sender.resolve(resources)
    val time = if (bubble.isEdited) resources.getString(R.string.chat_bubble_time_edited, bubble.time) else bubble.time
    val content =
        listOfNotNull(
            bubble.forwardedFrom?.resolve(resources),
            bubble.reply?.let { reply ->
                val text = reply.text.resolve(resources)
                reply.sender?.let { resources.getString(R.string.chat_bubble_reply, it.resolve(resources), text) }
                    ?: text
            },
            bubble.contentLabel?.resolve(resources),
            bubble.text.spokenText(bubble.spoilersRevealed, resources.getString(R.string.chat_bubble_spoiler)),
            bubble.linkPreview?.title,
            bubble.reactions.joinToString(", ") { reaction ->
                resources.getString(R.string.chat_bubble_reaction, reaction.label.spoken(resources), reaction.count)
            },
        ).filter(String::isNotBlank).joinToString(", ")
    val status = bubble.status?.let { resources.getString(it.spoken) }
    return if (status != null) {
        resources.getString(R.string.chat_bubble_description, sender, time, content, status)
    } else {
        resources.getString(R.string.chat_bubble_description_incoming, sender, time, content)
    }
}

/** The text, with [spoiler] in place of each hidden spoiler, so a screen reader does not give it away. */
private fun FormattedText.spokenText(
    spoilersRevealed: Boolean,
    spoiler: String,
): String {
    val spoilers = if (spoilersRevealed) emptyList() else entities.filter { it.type == TextEntityType.Spoiler }
    if (spoilers.isEmpty()) return text
    return buildString {
        var index = 0
        for (entity in spoilers.sortedBy { it.offset }) {
            val start = entity.offset.coerceIn(index, text.length)
            val end = (entity.offset + entity.length).coerceIn(start, text.length)
            append(text, index, start)
            append(spoiler)
            index = end
        }
        append(text, index, text.length)
    }
}

private fun ReactionLabel.spoken(resources: Resources): String =
    when (this) {
        is ReactionLabel.Emoji -> emoji
        ReactionLabel.CustomEmoji -> resources.getString(R.string.chat_reaction_custom_emoji)
        ReactionLabel.Paid -> resources.getString(R.string.chat_reaction_paid)
    }

private val OutgoingStatus.spoken: Int
    get() =
        when (this) {
            OutgoingStatus.Pending -> R.string.chat_status_pending
            OutgoingStatus.Failed -> R.string.chat_status_failed
            OutgoingStatus.Sent -> R.string.chat_status_sent
            OutgoingStatus.Read -> R.string.chat_status_read
        }
