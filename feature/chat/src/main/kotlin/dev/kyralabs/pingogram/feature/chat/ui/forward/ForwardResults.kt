package dev.kyralabs.pingogram.feature.chat.ui.forward

import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What came of the forward destination's sheets ([ForwardViewModel]), for the snackbars of the screen below it
 * (`ForwardNotices`): the destination is gone by the time its forwarding ends. Without a screen collecting them, they
 * are dropped (no replay): a result never shows on a screen opened later.
 */
@Singleton
class ForwardResults
    @Inject
    constructor() {
        private val flow =
            MutableSharedFlow<ChatEffect>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** The results, as they come. */
        internal val effects: SharedFlow<ChatEffect> = flow.asSharedFlow()

        /** Hands [effect] to the screen showing results; from any thread. */
        internal fun send(effect: ChatEffect) {
            flow.tryEmit(effect)
        }

        private companion object {
            const val BUFFER = 4
        }
    }
