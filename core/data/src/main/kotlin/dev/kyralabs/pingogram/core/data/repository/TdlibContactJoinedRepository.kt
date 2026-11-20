package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.ContactJoinedRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ContactJoinedRepository] on TDLib's option `disable_contact_registered_notifications` (true: no notices), read with
 * `getOption` once per collection and followed through `updateOption`, to which it subscribes first; no cache, as TDLib
 * keeps the option. An option TDLib cannot read counts as Telegram's default, notices on.
 */
@AccountScoped
class TdlibContactJoinedRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope private val scope: CoroutineScope,
    ) : ContactJoinedRepository {
        override val notifies: Flow<Boolean> =
            client.booleanOption(OPTION, scope).map { disabled -> !disabled }

        override suspend fun setNotifies(notifies: Boolean): Outcome<Unit> =
            client.request(TdApi.SetOption(OPTION, TdApi.OptionValueBoolean(!notifies))).map {}

        private companion object {
            const val OPTION = "disable_contact_registered_notifications"
        }
    }
