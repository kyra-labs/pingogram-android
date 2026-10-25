package dev.kyralabs.pingogram.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.di.AccountDependencies

/**
 * Hands each account's graph (`core:data`'s `AccountComponent`, `docs/adr/0031-multiple-accounts.md`) what it takes
 * from the app's: Hilt implements [AccountDependencies] as an entry point of the app's graph.
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountsModule {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppAccountDependencies : AccountDependencies

    @Provides
    fun accountDependencies(
        @ApplicationContext context: Context,
    ): AccountDependencies = EntryPointAccessors.fromApplication(context, AppAccountDependencies::class.java)
}
