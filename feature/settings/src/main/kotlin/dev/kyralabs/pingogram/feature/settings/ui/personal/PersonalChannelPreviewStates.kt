package dev.kyralabs.pingogram.feature.settings.ui.personal

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.persistentListOf

/** The states of the Personal channel page, shared by previews and screenshot tests. */
internal object PersonalChannelPreviewStates {
    val content =
        PersonalChannelUiState.Content(
            channels =
                persistentListOf(
                    PersonalChannelItem(-1_001, "Ada's notes"),
                    PersonalChannelItem(-1_002, "Analytical Engine news"),
                ),
            chosen = -1_001,
        )

    val saving = content.copy(chosen = -1_002, isSaving = true)

    val noChannels = PersonalChannelUiState.Content(channels = persistentListOf(), chosen = 0)

    val loading = PersonalChannelUiState.Loading

    val error = PersonalChannelUiState.Failed(DomainError.Network)
}
