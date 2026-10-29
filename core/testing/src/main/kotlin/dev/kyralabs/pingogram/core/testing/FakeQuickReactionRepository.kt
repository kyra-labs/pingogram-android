package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [QuickReactionRepository] in memory: [initial] as announced, [failure] making the setter fail. */
class FakeQuickReactionRepository(
    initial: ReactionType? = ReactionType.Emoji("👍"),
) : QuickReactionRepository {
    /** The quick reaction; tests set it to play TDLib's update. */
    val announced = MutableStateFlow(initial)

    /** What the setter fails with; null takes the reaction. */
    var failure: DomainError? = null

    /** Whether the setter announces the reaction at once, as TDLib's update would. */
    var announces: Boolean = true

    /** What the setter was asked for, in order. */
    val requested = mutableListOf<ReactionType>()

    override val quickReaction: StateFlow<ReactionType?> = announced

    override suspend fun setQuickReaction(reaction: ReactionType): Outcome<Unit> {
        requested += reaction
        failure?.let { return Outcome.Failure(it) }
        if (announces) announced.value = reaction
        return Outcome.Success(Unit)
    }
}
