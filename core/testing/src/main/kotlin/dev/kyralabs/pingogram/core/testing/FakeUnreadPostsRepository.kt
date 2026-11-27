package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.UnreadPostsRepository

/** [UnreadPostsRepository] in memory: each chat's [posts], or [failure] for every chat while it is set. */
class FakeUnreadPostsRepository(
    val posts: MutableMap<Long, List<Message>> = mutableMapOf(),
    var failure: Outcome.Failure? = null,
) : UnreadPostsRepository {
    override suspend fun postsAfter(
        chatId: Long,
        afterMessageId: Long,
        limit: Int,
    ): Outcome<List<Message>> =
        failure ?: Outcome.Success(posts[chatId].orEmpty().filter { it.id > afterMessageId }.take(limit))
}
