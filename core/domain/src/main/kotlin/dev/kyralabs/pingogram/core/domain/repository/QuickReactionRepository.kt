package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import kotlinx.coroutines.flow.StateFlow

/**
 * The account's quick reaction, which a double tap on a message adds: td_api.tl's default reaction, announced once
 * per start (`updateDefaultReactionType`) and shared with the account's other apps.
 */
interface QuickReactionRepository {
    /** The quick reaction as TDLib last announced it; null until it has. */
    val quickReaction: StateFlow<ReactionType?>

    /** Makes [reaction] the account's quick reaction (`setDefaultReactionType`); TDLib then announces it. */
    suspend fun setQuickReaction(reaction: ReactionType): Outcome<Unit>
}
