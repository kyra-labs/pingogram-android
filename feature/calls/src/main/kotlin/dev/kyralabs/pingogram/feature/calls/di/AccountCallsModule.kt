package dev.kyralabs.pingogram.feature.calls.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.feature.calls.AccountCallUsers
import dev.kyralabs.pingogram.feature.calls.AllAccounts
import dev.kyralabs.pingogram.feature.calls.AllAccountsCallRepository
import dev.kyralabs.pingogram.feature.calls.CallUsers

/** The calls of every account (`docs/adr/0031-multiple-accounts.md`): their repository and their people. */
@Module
@InstallIn(SingletonComponent::class)
internal interface AccountCallsModule {
    @Binds
    @AllAccounts
    fun allAccountsCalls(calls: AllAccountsCallRepository): CallRepository

    @Binds
    fun callUsers(users: AccountCallUsers): CallUsers
}
