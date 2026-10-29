package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.repository.ContactJoinedRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    ) : ContactJoinedRepository {
        override val notifies: Flow<Boolean> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.collect { update ->
                        if (update is TdApi.UpdateOption && update.name == OPTION) send(!update.value.isTrue())
                    }
                }
                val read = client.request(TdApi.GetOption(OPTION))
                send(!((read as? Outcome.Success)?.value?.isTrue() ?: false))
            }.distinctUntilChanged()

        override suspend fun setNotifies(notifies: Boolean): Outcome<Unit> =
            client.request(TdApi.SetOption(OPTION, TdApi.OptionValueBoolean(!notifies))).map {}

        private companion object {
            const val OPTION = "disable_contact_registered_notifications"
        }
    }

/** An option's value as a flag: only `true` is true; empty (unset) and other kinds are false. */
private fun TdApi.OptionValue?.isTrue(): Boolean = (this as? TdApi.OptionValueBoolean)?.value == true
