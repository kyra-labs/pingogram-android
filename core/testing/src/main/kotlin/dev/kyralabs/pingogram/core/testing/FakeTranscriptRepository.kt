package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Transcript
import dev.kyralabs.pingogram.core.domain.repository.TranscriptRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [TranscriptRepository] in memory: [saved] by chat, then by message. */
class FakeTranscriptRepository : TranscriptRepository {
    val saved = MutableStateFlow<Map<Long, Map<Long, Transcript>>>(emptyMap())

    override fun transcripts(chatId: Long): Flow<ImmutableMap<Long, Transcript>> =
        saved.map { it[chatId]?.toImmutableMap() ?: persistentMapOf() }.distinctUntilChanged()

    override suspend fun save(
        chatId: Long,
        messageId: Long,
        transcript: Transcript,
    ): Outcome<Unit> {
        saved.update { all -> all + (chatId to all[chatId].orEmpty() + (messageId to transcript)) }
        return Outcome.Success(Unit)
    }
}
