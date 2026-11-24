package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.drafts.DraftsUiState
import dev.kyralabs.pingogram.feature.chats.ui.drafts.NO_MESSAGE
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Drafts page's states, named, for its previews. */
internal class DraftsPreviewStates : PreviewParameterProvider<DraftsUiState> {
    private val messages = SearchPreviewData.messages

    /** Global search's preview messages as drafts: no sender, no message, the user's own unsent words. */
    private val drafts =
        persistentListOf(
            messages[0].copy(
                messageId = NO_MESSAGE,
                sender = null,
                preview = UiText.Plain("Could you send me the notes on the engine before Friday?"),
            ),
            messages[1].copy(
                messageId = NO_MESSAGE,
                sender = null,
                preview = UiText.resource(UiR.string.preview_voice_message),
            ),
            messages[2].copy(messageId = NO_MESSAGE, sender = null, preview = UiText.Plain("Congratulations on the")),
        )

    private val states =
        listOf(
            "Drafts" to DraftsUiState.Content(drafts),
            "One draft" to DraftsUiState.Content(persistentListOf(drafts[0])),
            "Loading" to DraftsUiState.Loading,
            "No drafts" to DraftsUiState.Empty,
        )

    override val values: Sequence<DraftsUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
