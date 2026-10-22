package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructText

/**
 * What screen readers say for a bubble, which they read as one (`docs/DESIGN_SYSTEM.md` §9): "{sender}, {time}:
 * {content}, {status}", the status only for outgoing messages. The content is the forward's origin, the reply, the
 * kind of media, the text (hidden spoilers read "spoiler"), the link preview's title, the reactions, a self-destruct
 * timer and how many buttons a bot attached. Built in the bubble's `semantics` block, so only when something reads the
 * semantics tree (`docs/PERFORMANCE.md` §4).
 */
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
            bubble.selfDestructSeconds?.let {
                resources.getString(R.string.chat_bubble_self_destruct, selfDestructText(resources, it))
            },
            bubble.thread?.let { threadLinkText(resources, it) },
            bubble.keyboard?.let { keyboard ->
                val count = keyboard.rows.sumOf { it.size }
                resources.getQuantityString(R.plurals.bot_buttons_count, count, count)
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

/** "React with 👍", or "Remove reaction 👍" for a reaction the user chose ([isChosen]), for screen readers. */
internal fun reactionToggleLabel(
    resources: Resources,
    label: ReactionLabel,
    isChosen: Boolean,
): String =
    resources.getString(
        if (isChosen) R.string.message_reaction_remove else R.string.message_reaction_add,
        label.spoken(resources),
    )

/**
 * A reaction on a message with how often it was added, for screen readers: "React with 👍, 3 reactions", or "Remove
 * reaction 👍, …" if the user added it too; just "Star, 12 reactions" where it is not a button ([isButton]).
 */
internal fun reactionChipLabel(
    resources: Resources,
    chip: ReactionChip,
    isButton: Boolean,
): String {
    val what = if (isButton) reactionToggleLabel(resources, chip.label, chip.isChosen) else chip.label.spoken(resources)
    return resources.getQuantityString(R.plurals.message_reaction_count, chip.count, what, chip.count)
}

private fun ReactionLabel.spoken(resources: Resources): String =
    when (this) {
        is ReactionLabel.Emoji -> emoji
        is ReactionLabel.CustomEmoji -> resources.getString(R.string.chat_reaction_custom_emoji)
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
