package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccountGraphs] over [Accounts]: each account's graph, by slot (`docs/adr/0031-multiple-accounts.md`), once the
 * registry is read.
 */
@Singleton
class DefaultAccountGraphs
    @Inject
    constructor(
        private val devices: Accounts,
        @ApplicationScope scope: CoroutineScope,
    ) : AccountGraphs {
        override val accounts: Flow<ImmutableList<AccountGraph>> =
            flow {
                devices.awaitLoaded()
                emitAll(
                    devices.registry
                        .map { it.slots }
                        .distinctUntilChanged()
                        .map { graphs() },
                )
            }

        override val activeSlot: StateFlow<Int> =
            devices.registry
                .map { it.active }
                .stateIn(scope, SharingStarted.Eagerly, devices.registry.value.active)

        override fun repositories(slot: Int): AccountRepositories? =
            devices.all().firstOrNull { it.slot.index == slot }?.component

        private fun graphs(): ImmutableList<AccountGraph> =
            devices.all().map { AccountGraph(it.slot.index, it.component) }.toImmutableList()
    }
