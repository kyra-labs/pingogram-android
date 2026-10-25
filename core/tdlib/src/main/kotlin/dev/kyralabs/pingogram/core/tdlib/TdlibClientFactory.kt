package dev.kyralabs.pingogram.core.tdlib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes the TDLib clients of the accounts after the first (`docs/adr/0031-multiple-accounts.md`), each with its own
 * directories and database key ([AccountSlot]). The first account's client is the app's [TdlibClient] binding, so
 * tests that replace it replace the first account's.
 */
@Singleton
class TdlibClientFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val config: TdlibConfig,
        @Dispatcher(IO) private val io: CoroutineDispatcher,
        @Dispatcher(Default) private val default: CoroutineDispatcher,
    ) {
        /**
         * A client for [slot], which starts a new TDLib instance after `AuthorizationStateClosed` only while [kept]
         * says the account is still on the device.
         */
        fun create(
            slot: AccountSlot,
            kept: () -> Boolean,
        ): TdlibClient {
            require(slot != AccountSlot.First) { "The first account's client is the app's TdlibClient" }
            val keys = KeystoreDatabaseKeyProvider(context, config, slot)
            val log = AndroidTdlibLog(config.isDebug)
            return DefaultTdlibClient(
                native = JniTdlib,
                parametersProvider = AndroidTdlibParametersProvider(context, config, slot, keys, io),
                // TDLib's log is the process's: the first account's production directory holds it for every account.
                logSettings = {
                    TdlibLogSettings.of(config.isDebug, TdlibDirectories.of(context.filesDir, useTestDc = false))
                },
                log = log,
                scope = DefaultTdlibClient.clientScope(default, log),
                restartsAfterClose = kept,
            )
        }
    }
