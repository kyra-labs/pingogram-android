package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.domain.repository.ChatNoteRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [ChatNoteRepository] in memory, [all] by chat id; [failure] makes every change fail. */
class FakeChatNoteRepository(
    initial: ImmutableMap<Long, ChatNote> = persistentMapOf(),
) : ChatNoteRepository {
    val all = MutableStateFlow(initial)

    /** What every change fails with; null takes it. */
    var failure: DomainError? = null

    override val notes: Flow<ImmutableMap<Long, ChatNote>> = all

    override suspend fun set(note: ChatNote): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        val kept = note.kept()
        all.value = (if (kept.isEmpty) all.value - kept.chatId else all.value + (kept.chatId to kept)).toImmutableMap()
        return Outcome.Success(Unit)
    }
}
