package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** A chat folder keyword alerts may look in: its [id] and [title]. */
@Immutable
data class KeywordFolder(
    val id: Int,
    val title: String,
) {
    override fun toString() = "KeywordFolder(id=$id, …)"
}

/** What the empty page offers to add with one tap: the account's first name or username ([word], [kind]). */
@Immutable
data class KeywordSuggestion(
    val word: String,
    val kind: Kind,
) {
    enum class Kind { Name, Username }

    override fun toString() = "KeywordSuggestion(kind=$kind)"
}

/**
 * Keyword alerts' page (board 30): the [settings] (null until read), the user's chat [folders], what the empty page
 * suggests ([suggestions]), the folders being chosen in the sheet ([choosingFolders], null while it is closed), and
 * how many messages each word matched in the last week that are not read yet ([matchCounts], by word).
 */
@Immutable
data class KeywordAlertsUiState(
    val settings: KeywordAlertSettings? = null,
    val folders: ImmutableList<KeywordFolder> = persistentListOf(),
    val suggestions: ImmutableList<KeywordSuggestion> = persistentListOf(),
    val choosingFolders: ImmutableSet<Int>? = null,
    val matchCounts: ImmutableMap<String, Int> = persistentMapOf(),
) {
    /** Whether another word may be added. */
    val canAdd: Boolean get() = (settings?.words?.size ?: 0) < KeywordAlertSettings.MAX_WORDS
}

/** What the user does on the page, handled by [KeywordAlertsViewModel.onEvent]. */
sealed interface KeywordAlertsEvent {
    data class Add(
        val word: String,
    ) : KeywordAlertsEvent

    data class Remove(
        val word: String,
    ) : KeywordAlertsEvent

    /** All chats or muted chats only; chosen folders go through the sheet ([ChooseFolders]). */
    data class Where(
        val where: KeywordScope,
    ) : KeywordAlertsEvent

    /** Opens the sheet of folders, starting from those chosen. */
    data object ChooseFolders : KeywordAlertsEvent

    data class ToggleFolder(
        val folderId: Int,
    ) : KeywordAlertsEvent

    /** "Done": looks in the folders chosen, or keeps where it looked when none is. */
    data object FoldersChosen : KeywordAlertsEvent

    data object CloseFolders : KeywordAlertsEvent

    data class AlertWhenMuted(
        val alert: Boolean,
    ) : KeywordAlertsEvent

    data class ShowInChatList(
        val show: Boolean,
    ) : KeywordAlertsEvent
}

/** One-off side effects of [KeywordAlertsViewModel], handled once by its route. */
sealed interface KeywordAlertsEffect {
    /** The word was added: the field empties. */
    data object Added : KeywordAlertsEffect

    data class ShowError(
        val error: DomainError,
    ) : KeywordAlertsEffect
}
