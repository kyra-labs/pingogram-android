package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ReplyQuote
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.canWrite
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.privateChat
import dev.kyralabs.pingogram.feature.chat.ui.reply.ReplyChatsSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample quote replies, replies in another chat and media replacements for previews and screenshot tests: the
 * composer quoting part of Ada's question, replying to a message of another chat, and editing a photo whose media can
 * be replaced; bubbles quoting part of a message, sent and received; the chat picker of "Reply in another chat",
 * loaded, loading and empty.
 */
internal object ReplyPreviewData {
    private const val ADA = ChatPreviewData.ADA

    /** [privateChat] replying to Ada's question, quoting part of it. */
    val quoting =
        privateChat.copy(
            composer =
                canWrite.copy(
                    bar =
                        ComposerBar.Reply(
                            messageId = 10,
                            sender = UiText.Plain("Ada Lovelace"),
                            preview = UiText.Plain("still on for tonight"),
                            isQuote = true,
                        ),
                ),
        )

    /** [privateChat] replying to a message of the Compose Developers group. */
    val replyingElsewhere =
        privateChat.copy(
            composer =
                canWrite.copy(
                    bar =
                        ComposerBar.Reply(
                            messageId = 25,
                            sender =
                                UiText.resource(
                                    R.string.composer_reply_elsewhere,
                                    "Linus Torvalds",
                                    "Compose Developers",
                                ),
                            preview = UiText.Plain("Here's the write-up on the new lazy layouts"),
                            isElsewhere = true,
                        ),
                ),
        )

    /** [privateChat] editing the caption of the user's photo, whose media can be replaced. */
    val editingMedia =
        privateChat.copy(
            composer =
                canWrite.copy(
                    bar =
                        ComposerBar.Edit(
                            messageId = 6,
                            preview = UiText.resource(UiR.string.preview_photo),
                            canBeEmpty = true,
                            replace = ReplaceMedia.Media,
                        ),
                    attachOptions = persistentListOf(),
                ),
        )

    /** A private chat whose messages quote part of the messages they reply to, one sent and one received. */
    val quotes =
        privateChat.copy(
            content =
                content(
                    bubble(
                        13,
                        "It is! Doors open at seven.",
                        time = "9:48 AM",
                        sender = UiText.Plain("Ada"),
                        senderId = ADA,
                        reply =
                            ReplyQuote(
                                UiText.resource(UiR.string.preview_sender_you),
                                0,
                                UiText.Plain("the meetup"),
                                messageId = 12,
                                isQuote = true,
                            ),
                    ),
                    bubble(
                        12,
                        "Great, I'll bring the slides.",
                        time = "9:47 AM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        position = GroupPosition.Single,
                        reply =
                            ReplyQuote(
                                UiText.Plain("Ada Lovelace"),
                                ADA,
                                UiText.Plain("still on for tonight"),
                                messageId = 10,
                                isQuote = true,
                            ),
                    ),
                    bubble(
                        10,
                        "Is the meetup still on for tonight?",
                        time = "9:41 AM",
                        sender = UiText.Plain("Ada"),
                        senderId = ADA,
                    ),
                ),
        )

    /** The chats to reply in, Saved Messages first. */
    val chatPicker =
        ReplyChatsSheet(
            messageId = 10,
            preview = UiText.Plain("Is the meetup still on for tonight?"),
            targets = ForwardPreviewData.targets.filterNot { it.isSecret }.toImmutableList(),
        )

    /** [chatPicker] while the chats load. */
    val chatPickerLoading = chatPicker.copy(targets = persistentListOf(), isLoading = true)

    /** [chatPicker] where no chat takes the reply. */
    val chatPickerEmpty = chatPicker.copy(targets = persistentListOf())
}
