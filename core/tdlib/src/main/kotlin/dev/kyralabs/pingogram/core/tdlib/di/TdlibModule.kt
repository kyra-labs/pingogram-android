package dev.kyralabs.pingogram.core.tdlib.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.tdlib.AndroidTdlibParametersProvider
import dev.kyralabs.pingogram.core.tdlib.DatabaseKeyProvider
import dev.kyralabs.pingogram.core.tdlib.DefaultTdlibClient
import dev.kyralabs.pingogram.core.tdlib.KeystoreDatabaseKeyProvider
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibParametersProvider

/** Needs a `TdlibConfig` and the `@Dispatcher` bindings, which `:app` provides. */
@Module
@InstallIn(SingletonComponent::class)
interface TdlibModule {
    @Binds
    fun tdlibClient(client: DefaultTdlibClient): TdlibClient

    @Binds
    fun parametersProvider(provider: AndroidTdlibParametersProvider): TdlibParametersProvider

    @Binds
    fun databaseKeyProvider(provider: KeystoreDatabaseKeyProvider): DatabaseKeyProvider
}
