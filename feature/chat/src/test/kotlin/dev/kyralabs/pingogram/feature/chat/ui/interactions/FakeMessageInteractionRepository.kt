package dev.kyralabs.pingogram.feature.chat.ui.interactions

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddedReactionsPage
import dev.kyralabs.pingogram.core.domain.model.MessageViewer
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.MessageInteractionRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Answers [viewers] with [onViewers] and [addedReactions] with [onReactions], recording every request ([requests]:
 * `viewers:<chat>:<message>`, `reactions:<chat>:<message>:<type or all>:<offset>`).
 */
class FakeMessageInteractionRepository : MessageInteractionRepository {
    var onViewers: suspend () -> Outcome<ImmutableList<MessageViewer>> = { Outcome.Success(persistentListOf()) }

    var onReactions: suspend (type: ReactionType?, offset: String) -> Outcome<AddedReactionsPage> = { _, _ ->
        Outcome.Success(AddedReactionsPage(0, persistentListOf(), nextOffset = ""))
    }

    val requests = mutableListOf<String>()

    override suspend fun viewers(
        chatId: Long,
        messageId: Long,
    ): Outcome<ImmutableList<MessageViewer>> {
        requests += "viewers:$chatId:$messageId"
        return onViewers()
    }

    override suspend fun addedReactions(
        chatId: Long,
        messageId: Long,
        type: ReactionType?,
        offset: String,
        limit: Int,
    ): Outcome<AddedReactionsPage> {
        requests += "reactions:$chatId:$messageId:${type ?: "all"}:$offset"
        return onReactions(type, offset)
    }
}
