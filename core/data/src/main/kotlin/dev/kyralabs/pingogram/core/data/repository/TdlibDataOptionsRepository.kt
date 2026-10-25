package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.repository.DataOptionsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DataOptionsRepository] on TDLib: its option `prefer_ipv6`, read with `getOption` once per collection and followed
 * through `updateOption`, to which it subscribes first; and `clearAllDraftMessages` without secret chats. No cache, as
 * TDLib keeps both.
 */
@Singleton
class TdlibDataOptionsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : DataOptionsRepository {
        override val prefersIpv6: Flow<Boolean> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.collect { update ->
                        if (update is TdApi.UpdateOption && update.name == PREFER_IPV6) send(update.value.isOn())
                    }
                }
                val read = client.request(TdApi.GetOption(PREFER_IPV6))
                send((read as? Outcome.Success)?.value.isOn())
            }.distinctUntilChanged()

        override suspend fun setPrefersIpv6(prefers: Boolean): Outcome<Unit> =
            client.request(TdApi.SetOption(PREFER_IPV6, TdApi.OptionValueBoolean(prefers))).map {}

        override suspend fun deleteCloudDrafts(): Outcome<Unit> =
            client.request(TdApi.ClearAllDraftMessages(true)).map {}

        private companion object {
            const val PREFER_IPV6 = "prefer_ipv6"
        }
    }

/** An option's value as a flag: only `true` is on; empty (unset) and other kinds are off. */
private fun TdApi.OptionValue?.isOn(): Boolean = (this as? TdApi.OptionValueBoolean)?.value == true
