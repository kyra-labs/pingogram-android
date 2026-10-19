package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ReactionChip
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.ADA
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.SECOND_PHOTO_FILE_ID
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.ada
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.canWrite
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.privateChat
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.reactions
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample conversations of bubble shapes for previews and screenshot tests (`docs/DESIGN_SYSTEM.md` §4): groups on both
 * sides with a photo among them, emoji-only messages and reaction badges. Items are listed newest first.
 */
internal object BubblePreviewData {
    /**
     * Groups of bubbles on both sides (first, middle and last), with the extra small corners where they meet on the
     * sender's side, a photo in the middle of a group, one-line pills and multi-line bubbles.
     */
    val groupedBubbles =
        ChatUiState(
            composer = canWrite,
            header = privateChat.header,
            content =
                content(
                    bubble(
                        27,
                        "Perfect, see you at the pier",
                        time = "6:12 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        position = GroupPosition.Last,
                    ),
                    bubble(
                        26,
                        "",
                        time = "6:11 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        position = GroupPosition.Middle,
                        content = ContentPreviewData.photo(minithumbnail = null, firstFileId = SECOND_PHOTO_FILE_ID),
                        contentLabel = UiText.resource(UiR.string.preview_photo),
                    ),
                    bubble(
                        25,
                        "This is where we sat last time",
                        time = "6:11 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        position = GroupPosition.First,
                    ),
                    bubble(
                        24,
                        "The ferry leaves at seven, so let's meet a little before at the usual place by the pier.",
                        time = "6:09 PM",
                        sender = ada,
                        senderId = ADA,
                        position = GroupPosition.Last,
                    ),
                    bubble(
                        23,
                        "Tickets are booked",
                        time = "6:09 PM",
                        sender = ada,
                        senderId = ADA,
                        position = GroupPosition.Middle,
                    ),
                    bubble(
                        22,
                        "Good news!",
                        time = "6:08 PM",
                        sender = ada,
                        senderId = ADA,
                        position = GroupPosition.First,
                    ),
                    bubble(
                        21,
                        "Any news about Saturday?",
                        time = "5:58 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                    ),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )

    /**
     * Emoji-only messages (one emoji, three of them, a flag) shown large without a bubble, and reaction badges on the
     * bubbles' far corners: one, two (one chosen), and more than fit, with "+N".
     */
    val emojiAndReactions =
        ChatUiState(
            composer = canWrite,
            header = privateChat.header,
            content =
                content(
                    bubble(
                        36,
                        "😄",
                        time = "8:03 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        isEmojiOnly = true,
                    ),
                    bubble(
                        35,
                        "Which one should we take?",
                        time = "8:02 PM",
                        sender = ada,
                        senderId = ADA,
                        reactions =
                            reactions(
                                ReactionChip(ReactionLabel.Emoji("👍"), 4, isChosen = true),
                                ReactionChip(ReactionLabel.Emoji("😂"), 2, isChosen = false),
                                ReactionChip(ReactionLabel.Emoji("🔥"), 1, isChosen = false),
                                ReactionChip(ReactionLabel.Emoji("🎉"), 1, isChosen = false),
                                ReactionChip(ReactionLabel.Emoji("❤️"), 1, isChosen = false),
                            ),
                    ),
                    bubble(
                        34,
                        "The early ferry, then breakfast on the island",
                        time = "8:01 PM",
                        isOutgoing = true,
                        status = OutgoingStatus.Read,
                        reactions =
                            reactions(
                                ReactionChip(ReactionLabel.Emoji("❤️"), 1, isChosen = false),
                                ReactionChip(ReactionLabel.Emoji("😮"), 2, isChosen = true),
                            ),
                    ),
                    bubble(
                        33,
                        "cat version would be \"when i was on top of the fridge\" and honestly same level of " +
                            "achievement",
                        time = "7:58 PM",
                        sender = ada,
                        senderId = ADA,
                        reactions = reactions(ReactionChip(ReactionLabel.Emoji("😂"), 1, isChosen = false)),
                    ),
                    bubble(32, "🇯🇵", time = "7:57 PM", sender = ada, senderId = ADA, isEmojiOnly = true),
                    bubble(31, "🌊⛴️🏝️", time = "7:56 PM", sender = ada, senderId = ADA, isEmojiOnly = true),
                    ChatItem.DateSeparator(epochDay = 2, date = today),
                ),
        )
}
