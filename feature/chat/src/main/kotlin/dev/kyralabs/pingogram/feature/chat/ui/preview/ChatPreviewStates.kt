package dev.kyralabs.pingogram.feature.chat.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState

/** Every state of the conversation screen, for its previews. */
internal class ChatPreviewStates : PreviewParameterProvider<ChatUiState> {
    override val values: Sequence<ChatUiState> =
        with(ChatPreviewData) {
            sequenceOf(
                privateChat,
                groupChat,
                PinnedPreviewData.privateWithPin,
                PinnedPreviewData.groupWithPins,
                channel,
                ShareButtonPreviewData.channel,
                ThreadPreviewData.channelWithComments,
                ThreadPreviewData.comments,
                SponsoredPreviewData.channel,
                TranslationPreviewData.offered,
                TranslationPreviewData.translated,
                TranslationPreviewData.rightToLeft,
                serviceMessages,
                formattedText,
                BubblePreviewData.groupedBubbles,
                BubblePreviewData.emojiAndReactions,
                mediaPhotos,
                mediaFiles,
                voicePlaying,
                cannotWrite,
                ownActionsSheet,
                incomingActionsSheet,
                reactionPickerSheet,
                deleteConfirmation,
                SelectionPreviewData.selecting,
                SelectionPreviewData.reporting,
                replying,
                ReplyPreviewData.quoting,
                ReplyPreviewData.quotes,
                ReplyPreviewData.replyingElsewhere,
                editing,
                ReplyPreviewData.editingMedia,
                loading,
                empty,
                error,
                notFound,
            )
        }
}
