package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [QuickReactionRepository] on TDLib: the reaction of `updateDefaultReactionType` (a [TdlibUpdateCache], as TDLib
 * announces it once per start and has no function that reads it), changed with `setDefaultReactionType`, whose update
 * the cache then holds. The paid reaction cannot be the quick reaction.
 */
@AccountScoped
class TdlibQuickReactionRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : QuickReactionRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore<Announced>(client, scope, Announced(null)) { state, update ->
                if (update is TdApi.UpdateDefaultReactionType) {
                    Announced(update.reactionType.toReactionType())
                } else {
                    state
                }
            }

        override val quickReaction: StateFlow<ReactionType?> =
            store.state.map { it.reaction }.stateIn(scope, SharingStarted.Eagerly, null)

        override suspend fun setQuickReaction(reaction: ReactionType): Outcome<Unit> {
            val type =
                reaction.toTdReactionType()
                    ?: return Outcome.Failure(DomainError.Unknown(code = 0, message = "Not a default reaction"))
            return client.request(TdApi.SetDefaultReactionType(type)).map {}
        }

        /** What TDLib announced last; a holder, as [UpdateStore] keeps non-null states. */
        private data class Announced(
            val reaction: ReactionType?,
        )
    }
