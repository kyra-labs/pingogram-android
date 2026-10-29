package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTopicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** [SavedMessagesTopicRepository] in memory: [topicList] is what TDLib announced, [loadMore] answers [loadAnswer]. */
class FakeSavedMessagesTopicRepository(
    topics: List<SavedMessagesTopic> = emptyList(),
) : SavedMessagesTopicRepository {
    val topicList = MutableStateFlow<ImmutableList<SavedMessagesTopic>>(topics.toImmutableList())

    override val topics = topicList

    var loadAnswer: Outcome<Boolean> = Outcome.Success(false)

    var loads = 0
        private set

    override suspend fun loadMore(): Outcome<Boolean> {
        loads++
        return loadAnswer
    }

    fun set(vararg topics: SavedMessagesTopic) {
        topicList.value = topics.toList().toImmutableList()
    }

    companion object {
        val NONE: ImmutableList<SavedMessagesTopic> = persistentListOf()
    }
}
