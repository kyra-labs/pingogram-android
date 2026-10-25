package dev.kyralabs.pingogram.core.data.di

import dagger.BindsInstance
import dagger.Component
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkTypeReporter
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient

/**
 * One account's graph (`docs/adr/0031-multiple-accounts.md`): its TDLib client and every repository that talks to it,
 * each with its own caches. A Dagger component of its own rather than a Hilt child component, as the app-wide graph
 * binds the same interfaces to the active account's ([ActiveAccountModule]); what it needs from the app comes through
 * [AccountDependencies]. [Accounts] makes one per account.
 */
@AccountScoped
@Component(
    dependencies = [AccountDependencies::class],
    modules = [RepositoryModule::class, UpdateCacheModule::class],
)
interface AccountComponent : AccountRepositories {
    /** Every cache of this account, made before its client starts, so none misses an update. */
    fun updateCaches(): Set<@JvmSuppressWildcards TdlibUpdateCache>

    fun networkTypeReporter(): TdlibNetworkTypeReporter

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance slot: AccountSlot,
            @BindsInstance client: TdlibClient,
            dependencies: AccountDependencies,
        ): AccountComponent
    }
}
