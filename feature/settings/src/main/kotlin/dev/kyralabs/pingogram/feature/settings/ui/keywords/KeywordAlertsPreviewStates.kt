package dev.kyralabs.pingogram.feature.settings.ui.keywords

import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** The states of the keyword alerts page, shared by previews and screenshot tests. */
internal object KeywordAlertsPreviewStates {
    private val folders = persistentListOf(KeywordFolder(1, "Work"), KeywordFolder(2, "Family"))

    val words =
        KeywordAlertsUiState(
            settings =
                KeywordAlertSettings(
                    words = persistentListOf("launch", "Pingogram", "0.9.4", "@ada"),
                    where = KeywordScope.Folders(persistentSetOf(1)),
                ),
            folders = folders,
        )

    val empty =
        KeywordAlertsUiState(
            settings = KeywordAlertSettings(),
            folders = folders,
            suggestions =
                persistentListOf(
                    KeywordSuggestion("Ada", KeywordSuggestion.Kind.Name),
                    KeywordSuggestion("@ada.l", KeywordSuggestion.Kind.Username),
                ),
        )

    val full =
        KeywordAlertsUiState(
            settings =
                KeywordAlertSettings(
                    words = List(KeywordAlertSettings.MAX_WORDS) { "word${it + 1}" }.toImmutableList(),
                    where = KeywordScope.MutedChats,
                    alertWhenMuted = false,
                    showInChatList = true,
                ),
            folders = persistentListOf(),
        )

    val choosingFolders = words.copy(choosingFolders = persistentSetOf(1))
}
