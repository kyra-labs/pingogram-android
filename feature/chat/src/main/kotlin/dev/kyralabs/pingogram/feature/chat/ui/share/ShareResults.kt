package dev.kyralabs.pingogram.feature.chat.ui.share

import dev.kyralabs.pingogram.core.domain.model.ShareResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What came of sending shares to several chats ([ShareViewModel]), for the app shell's snackbars (`ShareNotices`): the
 * share sheet is gone by the time the sending ends. Without a shell collecting them, they are dropped (no replay).
 */
@Singleton
class ShareResults
    @Inject
    constructor() {
        private val flow =
            MutableSharedFlow<ShareResult>(extraBufferCapacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** The results, as they come. */
        internal val results: SharedFlow<ShareResult> = flow.asSharedFlow()

        /** Hands [result] to the shell; from any thread. */
        internal fun send(result: ShareResult) {
            flow.tryEmit(result)
        }

        private companion object {
            const val BUFFER = 4
        }
    }
