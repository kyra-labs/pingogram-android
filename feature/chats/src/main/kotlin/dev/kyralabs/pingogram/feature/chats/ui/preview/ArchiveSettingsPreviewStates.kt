package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.feature.chats.ui.archive.ArchiveSettingsContent
import dev.kyralabs.pingogram.feature.chats.ui.archive.ArchiveSettingsUiState

/** The archive settings page's states, for its previews and goldens. */
internal object ArchiveSettingsPreviewData {
    /** New chats archived where Telegram offers it; unmuted chats kept, which keeps folders' too. */
    val content =
        ArchiveSettingsUiState(
            settings =
                ArchiveSettingsContent.Content(
                    settings =
                        ArchiveSettings(
                            archiveAndMuteNewChatsFromNonContacts = true,
                            keepUnmutedChatsArchived = false,
                            keepChatsFromFoldersArchived = true,
                        ),
                    canArchiveAndMuteNewChats = true,
                ),
            rowStyle = ArchiveRowStyle.Compact,
        )

    /** Telegram does not offer archiving new chats; every unmuted chat is kept; the row hidden. */
    val unavailable =
        ArchiveSettingsUiState(
            settings =
                ArchiveSettingsContent.Content(
                    settings = ArchiveSettings(keepUnmutedChatsArchived = true),
                    canArchiveAndMuteNewChats = false,
                ),
            rowStyle = ArchiveRowStyle.Hidden,
        )

    val loading = ArchiveSettingsUiState()

    val error = ArchiveSettingsUiState(settings = ArchiveSettingsContent.Error(DomainError.Network))
}

/** Every state of the archive settings page, named in the preview pane. */
internal class ArchiveSettingsPreviewStates : PreviewParameterProvider<ArchiveSettingsUiState> {
    private val states =
        listOf(
            "Content" to ArchiveSettingsPreviewData.content,
            "Unavailable" to ArchiveSettingsPreviewData.unavailable,
            "Loading" to ArchiveSettingsPreviewData.loading,
            "Error" to ArchiveSettingsPreviewData.error,
        )

    override val values: Sequence<ArchiveSettingsUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
