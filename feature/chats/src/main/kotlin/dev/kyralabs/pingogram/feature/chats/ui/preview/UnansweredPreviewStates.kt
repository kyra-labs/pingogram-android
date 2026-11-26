package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.unanswered.UnansweredUiState
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Unanswered page's states for previews and goldens: people waiting, one, loading and none. */
internal class UnansweredPreviewStates : PreviewParameterProvider<UnansweredUiState> {
    private val messages = SearchPreviewData.messages

    /** Global search's preview messages as people's last messages, without a sender: they are private chats. */
    private val chats =
        persistentListOf(
            messages[0].copy(sender = null, preview = UiText.Plain("Are we still on for Thursday?")),
            messages[1].copy(
                chatTitle = UiText.Plain("Grace Hopper"),
                avatarShape = AvatarShape.Circle,
                sender = null,
                preview = UiText.resource(UiR.string.preview_voice_message),
            ),
            messages[2].copy(
                chatTitle = UiText.Plain("Mary Somerville"),
                avatarShape = AvatarShape.Circle,
                sender = null,
                preview = UiText.Plain("Here are the photos from the trip"),
            ),
        )

    private val states =
        listOf(
            "Waiting" to UnansweredUiState.Content(chats),
            "One" to UnansweredUiState.Content(persistentListOf(chats[0])),
            "Loading" to UnansweredUiState.Loading,
            "None" to UnansweredUiState.Empty,
        )

    override val values: Sequence<UnansweredUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
