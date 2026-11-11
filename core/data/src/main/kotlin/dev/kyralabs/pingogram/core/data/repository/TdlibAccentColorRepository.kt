package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import dev.kyralabs.pingogram.core.domain.repository.AccentColorRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [AccentColorRepository] on TDLib: a [TdlibUpdateCache] of `updateAccentColors`, which TDLib sends once per start and
 * again when Telegram changes its colors; each available color keeps the built-in color it is drawn like.
 */
@AccountScoped
class TdlibAccentColorRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : AccentColorRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, BUILT_IN, ::reduce)

        override val options: StateFlow<List<AccentColorOption>> = store.state

        override suspend fun setAccentColor(id: Int): Outcome<Unit> = client.request(TdApi.SetAccentColor(id, 0)).map {}
    }

/** The seven built-in colors, each drawn as itself. */
private val BUILT_IN = (0 until AccentColorOption.BUILT_IN_ACCENT_COLORS).map { AccentColorOption(it, it) }

/** The colors [update] makes available, in its order; the same list for other updates. */
private fun reduce(
    options: List<AccentColorOption>,
    update: TdApi.Update,
): List<AccentColorOption> {
    if (update !is TdApi.UpdateAccentColors) return options
    val builtInOf = update.colors.associate { it.id to it.builtInAccentColorId }
    return update.availableAccentColorIds.map { id -> AccentColorOption(id, builtInOf[id] ?: id) }
}
