package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.repository.KeywordHitRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [KeywordHitRepository] in memory: tests set [hits] and [emit] alerts as new matches would arrive. */
class FakeKeywordHitRepository(
    hits: List<KeywordHit> = emptyList(),
) : KeywordHitRepository {
    val hits = MutableStateFlow(hits)

    private val newAlerts = MutableSharedFlow<KeywordAlert>(extraBufferCapacity = 16)

    override val recent: Flow<ImmutableList<KeywordHit>> =
        this.hits.map { list -> list.sortedByDescending { it.date }.toImmutableList() }

    override val alerts: Flow<KeywordAlert> = newAlerts

    /** A new match, as the repository announces one. */
    suspend fun emit(alert: KeywordAlert) {
        hits.value = hits.value + alert.hit
        newAlerts.emit(alert)
    }

    override suspend fun clearChat(chatId: Long) {
        hits.value = hits.value.filterNot { it.chatId == chatId }
    }

    override suspend fun restore(hits: List<KeywordHit>) {
        val kept = this.hits.value
        this.hits.value =
            kept + hits.filter { hit -> kept.none { it.chatId == hit.chatId && it.messageId == hit.messageId } }
    }
}
