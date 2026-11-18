package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chats.ui.mentions.MentionsUiState
import kotlinx.collections.immutable.persistentListOf

/** The Mentions & replies page's states, named, for its previews. */
internal class MentionsPreviewStates : PreviewParameterProvider<MentionsUiState> {
    private val messages = SearchPreviewData.messages

    private val states =
        listOf(
            "Unread and earlier" to
                MentionsUiState.Content(
                    unread = persistentListOf(messages[1]),
                    earlier = persistentListOf(messages[0], messages[2]),
                ),
            "Earlier only" to MentionsUiState.Content(unread = persistentListOf(), earlier = messages),
            "Loading" to MentionsUiState.Loading,
            "No mentions" to MentionsUiState.Empty,
            "Error" to MentionsUiState.Error(DomainError.Network),
        )

    override val values: Sequence<MentionsUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
