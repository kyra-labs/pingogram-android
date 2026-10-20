package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.chats.ui.NewChatUiState

/** Every state of the New chat screen for its previews, named in the preview pane. */
internal class NewChatPreviewStates : PreviewParameterProvider<NewChatUiState> {
    private val states =
        listOf(
            "Contacts" to NewChatPreviewData.content,
            "Loading" to NewChatPreviewData.loading,
            "No contacts" to NewChatPreviewData.empty,
            "Error" to NewChatPreviewData.error,
        )

    override val values: Sequence<NewChatUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
