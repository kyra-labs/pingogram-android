package dev.kyralabs.pingogram.core.data.di

import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The accounts on the device and the one the screens show (`docs/adr/0031-multiple-accounts.md`). One account so far:
 * the first slot's, with the app's [TdlibClient]; adding and switching accounts come in stage 3.
 */
@Singleton
class Accounts
    @Inject
    constructor(
        client: TdlibClient,
        dependencies: Provider<AccountDependencies>,
    ) {
        /** The active account's graph, made the first time something asks for one of its repositories. */
        val active: AccountComponent by lazy {
            DaggerAccountComponent.factory().create(AccountSlot.First, client, dependencies.get())
        }
    }
