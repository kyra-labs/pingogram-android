package dev.kyralabs.pingogram.core.data.di

import javax.inject.Scope

/**
 * One instance per account (`docs/adr/0031-multiple-accounts.md`): what [AccountComponent] holds, each account with its
 * own TDLib client and caches. What belongs to the device, not to an account (the network, Battery Saver, the phone's
 * contacts), stays `@Singleton`.
 */
@Scope
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class AccountScoped
