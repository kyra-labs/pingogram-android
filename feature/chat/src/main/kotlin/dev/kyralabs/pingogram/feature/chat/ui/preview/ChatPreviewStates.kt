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
                channel,
                serviceMessages,
                formattedText,
                mediaPhotos,
                mediaFiles,
                loading,
                empty,
                error,
                notFound,
            )
        }
}
