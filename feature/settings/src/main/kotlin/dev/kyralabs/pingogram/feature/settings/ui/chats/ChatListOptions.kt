package dev.kyralabs.pingogram.feature.settings.ui.chats

import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.CompactChatListRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The Chats page's chat list settings, kept in three repositories: the chat list's own ([settings]), whether its rows
 * are compact ([compact], `REQUIREMENTS.md` FR-X19) and whether notes and names on this phone show ([notesAndNames],
 * FR-X20), read together as [choices].
 */
class ChatListOptions
    @Inject
    constructor(
        val settings: ChatListSettingsRepository,
        val compact: CompactChatListRepository,
        val notesAndNames: NotesAndNamesRepository,
    ) {
        /** All three, then every change of any. */
        internal val choices: Flow<ChatListChoices> =
            combine(settings.settings, compact.enabled, notesAndNames.enabled, ::ChatListChoices)
    }

/** The chat list's settings the Chats page shows, whether it is compact and whether notes and names show, together. */
internal data class ChatListChoices(
    val settings: ChatListSettings,
    val compact: Boolean,
    val notesAndNames: Boolean,
)
