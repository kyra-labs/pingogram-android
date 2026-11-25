package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.repository.ChatNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [LocalChatNamesRepository] from the account's [notes]: their names that are set, while [notesAndNames] is on. The
 * notes are read once and kept by their repository, so this adds no reading of its own.
 */
class NotesLocalChatNamesRepository(
    notes: ChatNoteRepository,
    notesAndNames: NotesAndNamesRepository,
) : LocalChatNamesRepository {
    override val names: Flow<ImmutableMap<Long, String>> =
        combine(notes.notes, notesAndNames.enabled) { all, enabled ->
            if (!enabled) {
                persistentMapOf()
            } else {
                all.values
                    .filter { it.name.isNotBlank() }
                    .associate { it.chatId to it.name }
                    .toImmutableMap()
            }
        }.distinctUntilChanged()
}
