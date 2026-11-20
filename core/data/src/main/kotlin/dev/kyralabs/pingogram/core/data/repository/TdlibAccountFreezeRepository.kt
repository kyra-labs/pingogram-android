package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import dev.kyralabs.pingogram.core.domain.repository.AccountFreezeRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [AccountFreezeRepository] on TDLib: the last `updateFreezeState` (a [TdlibUpdateCache], as TDLib announces it only
 * as an update and has no function to read it), reset when TDLib closes.
 */
@AccountScoped
class TdlibAccountFreezeRepository
    @Inject
    constructor(
        client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : AccountFreezeRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore(client, scope, FREEZE_UPDATES, Announced(null)) { state, update ->
                if (update is TdApi.UpdateFreezeState) Announced(update.toFreeze()) else state
            }

        override val freeze: StateFlow<AccountFreeze?> =
            store.state
                .map { it.freeze }
                .stateIn(scope, SharingStarted.Eagerly, null)

        /** What TDLib announced last; a holder, as [UpdateStore] keeps non-null states. */
        private data class Announced(
            val freeze: AccountFreeze?,
        )
    }

private fun TdApi.UpdateFreezeState.toFreeze(): AccountFreeze? =
    if (isFrozen) {
        AccountFreeze(
            frozenAt = freezingDate.toLong(),
            deletesAt = deletionDate.toLong(),
            appealLink,
        )
    } else {
        null
    }

/** The updates its store's reducer handles. */
private val FREEZE_UPDATES: Set<KClass<out TdApi.Update>> = setOf(TdApi.UpdateFreezeState::class)
