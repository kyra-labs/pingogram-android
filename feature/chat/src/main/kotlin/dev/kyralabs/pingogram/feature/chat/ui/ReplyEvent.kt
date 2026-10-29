package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia
import dev.kyralabs.pingogram.feature.chat.ui.reply.ChatMediaReplacement
import dev.kyralabs.pingogram.feature.chat.ui.reply.ChatReplyElsewhere

/**
 * Quote replies, replies in another chat, message links and replacing the media of a message being edited
 * (`REQUIREMENTS.md` FR-C5, FR-C6; `docs/DESIGN_SYSTEM.md` §6 "Message actions", "Composer"), for [ChatViewModel].
 */
sealed interface ReplyEvent : ChatEvent {
    /**
     * "Quote" in the text selection of lifted message [messageId]: the reply quotes the part of its text that is
     * selected, as the selection holds it ([selected]: the selected part of each text in the bubble, in their order).
     */
    data class Quote(
        val messageId: Long,
        val selected: List<String>,
    ) : ReplyEvent {
        override fun toString() = "Quote(messageId=$messageId, ${selected.size} parts)"
    }

    /** "Reply in another chat": the chats to reply in show in their sheet. */
    data class ReplyElsewhere(
        val messageId: Long,
    ) : ReplyEvent

    /** The sheet's search field holds [query]. */
    data class SearchChats(
        val query: String,
    ) : ReplyEvent {
        override fun toString() = "SearchChats(…)"
    }

    /** The sheet's list nears its end: the next page of chats loads. */
    data object LoadMoreChats : ReplyEvent

    /** Chat [chatId] was picked in the sheet: it opens with the reply in its composer. */
    data class PickChat(
        val chatId: Long,
    ) : ReplyEvent

    /** The sheet was dismissed. */
    data object DismissChats : ReplyEvent

    /** "Copy link" (or "Copy link to this thread") of message [messageId]: its link goes to the clipboard. */
    data class CopyLink(
        val messageId: Long,
    ) : ReplyEvent

    /** The edit bar's "Replace": the picker of the edited message's kind of media opens ([ReplyEffect.Pick]). */
    data object ReplaceMedia : ReplyEvent

    /** The picker returned the content at [uri] for the edited message: it opens in the attach preview. */
    data class ReplacementPicked(
        val uri: String,
    ) : ReplyEvent {
        override fun toString() = "ReplacementPicked(…)"
    }

    /** The attach preview's "Replace": the edited message's media is replaced, with the edit's text as its caption. */
    data object SaveReplacement : ReplyEvent
}

/** One-off effects of the replies and links, handled by the screen with the conversation's others. */
sealed interface ReplyEffect : ChatEffect {
    /**
     * Put the link [link] of a message on the clipboard; before Android 13, which confirms copies itself, a snackbar
     * says "Link copied".
     */
    data class CopyLink(
        val link: String,
    ) : ReplyEffect {
        override fun toString() = "CopyLink(…)"
    }

    /** Open the picker of [kind] for the media that replaces the edited message's; a snackbar where no app opens. */
    data class Pick(
        val kind: ReplaceMedia,
    ) : ReplyEffect

    /** The selected text could not be quoted (it is not the message's text): a snackbar says so. */
    data object QuoteUnavailable : ReplyEffect
}

/**
 * Hands this event to the part of [ChatViewModel] that handles it: the message actions check and start a quote, a reply
 * in another chat and a copied link ([actions]), [replacement] replaces an edited message's media, and [elsewhere]
 * holds the chat picker of a reply in another chat.
 */
internal fun ReplyEvent.dispatch(
    actions: ChatMessageActions,
    replacement: ChatMediaReplacement,
    elsewhere: ChatReplyElsewhere,
) {
    when (this) {
        is ReplyEvent.Quote -> {
            actions.quote(messageId, selected)
        }

        is ReplyEvent.ReplyElsewhere -> {
            actions.replyElsewhere(messageId)
        }

        is ReplyEvent.CopyLink -> {
            actions.copyLink(messageId)
        }

        ReplyEvent.ReplaceMedia, is ReplyEvent.ReplacementPicked, ReplyEvent.SaveReplacement -> {
            replacement.onEvent(this)
        }

        else -> {
            elsewhere.onEvent(this)
        }
    }
}
