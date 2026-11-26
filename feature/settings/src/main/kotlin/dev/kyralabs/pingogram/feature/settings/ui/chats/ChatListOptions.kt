package dev.kyralabs.pingogram.feature.settings.ui.chats

import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.CompactChatListRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The Chats page's chat list settings, kept in four repositories: the chat list's own ([settings]), whether its rows
 * are compact ([compact], `REQUIREMENTS.md` FR-X19), whether notes and names on this phone show ([notesAndNames],
 * FR-X20) and Unanswered's button and days ([unanswered], FR-X22), read together as [choices].
 */
class ChatListOptions
    @Inject
    constructor(
        val settings: ChatListSettingsRepository,
        val compact: CompactChatListRepository,
        val notesAndNames: NotesAndNamesRepository,
        val unanswered: UnansweredSettingsRepository,
    ) {
        /** All four, then every change of any. */
        internal val choices: Flow<ChatListChoices> =
            combine(settings.settings, compact.enabled, notesAndNames.enabled, unanswered.settings, ::ChatListChoices)
    }

/** The chat list's settings the Chats page shows: its own, compact rows, notes and names, and Unanswered's. */
internal data class ChatListChoices(
    val settings: ChatListSettings,
    val compact: Boolean,
    val notesAndNames: Boolean,
    val unanswered: UnansweredSettings,
)
