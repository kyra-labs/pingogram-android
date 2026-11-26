package dev.kyralabs.pingogram.core.domain.repository

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [LocalChatNamesRepository] from an account's [notes]: their names that are set, while [notesAndNames] is on. The
 * notes are read once and kept by their repository, so this adds no reading of its own; while the setting is off they
 * are not read at all, so nothing waits for their file. Plain flows, so the app's graph builds it for the active
 * account and the notifications for each account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesLocalChatNamesRepository(
    notes: ChatNoteRepository,
    notesAndNames: NotesAndNamesRepository,
) : LocalChatNamesRepository {
    override val names: Flow<ImmutableMap<Long, String>> =
        notesAndNames.enabled
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (!enabled) {
                    flowOf(persistentMapOf())
                } else {
                    notes.notes.map { all ->
                        all.values
                            .filter { it.name.isNotBlank() }
                            .associate { it.chatId to it.name }
                            .toImmutableMap()
                    }
                }
            }.distinctUntilChanged()
}
