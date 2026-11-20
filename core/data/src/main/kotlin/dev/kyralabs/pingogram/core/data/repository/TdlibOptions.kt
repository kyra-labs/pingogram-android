package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.data.updates.updatesOf
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * TDLib's boolean option [name]: read with `getOption` once per collection, then followed through `updateOption`, to
 * which it subscribes first (through the router of [scope]), so no change in between is missed. Unset, unreadable or
 * not a boolean, it is false.
 */
internal fun TdlibClient.booleanOption(
    name: String,
    scope: CoroutineScope,
): Flow<Boolean> =
    channelFlow {
        launch(start = CoroutineStart.UNDISPATCHED) {
            updateRouter(scope).updatesOf<TdApi.UpdateOption>().collect { update ->
                if (update.name == name) send(update.value.isTrue())
            }
        }
        val read = request(TdApi.GetOption(name))
        send((read as? Outcome.Success)?.value.isTrue())
    }.distinctUntilChanged()

/** Sets TDLib's boolean option [name] to [value]. */
internal suspend fun TdlibClient.setBooleanOption(
    name: String,
    value: Boolean,
): Outcome<Unit> = request(TdApi.SetOption(name, TdApi.OptionValueBoolean(value))).map {}

private fun TdApi.OptionValue?.isTrue(): Boolean = (this as? TdApi.OptionValueBoolean)?.value == true
