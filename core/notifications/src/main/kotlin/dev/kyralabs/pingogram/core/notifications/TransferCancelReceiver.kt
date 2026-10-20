package dev.kyralabs.pingogram.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The transfers notification's "Cancel" ([TransferNotificationBuilder]): cancels every transfer the user started
 * ([TransferRepository.cancelAll]); [TransfersWorker] then ends with them. TDLib takes the requests at once, so the
 * broadcast is held only while it answers. Not exported: only the app's own pending intent reaches it.
 */
class TransferCancelReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_CANCEL) return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, TransferEntryPoint::class.java)
        val pending = goAsync()
        entryPoint.applicationScope().launch {
            try {
                entryPoint.transferRepository().cancelAll()
            } finally {
                pending.finish()
            }
        }
    }

    /** What the receiver takes from the app's graph; it is no Hilt entry point itself, so tests can create it bare. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TransferEntryPoint {
        fun transferRepository(): TransferRepository

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    internal companion object {
        const val ACTION_CANCEL = "dev.kyralabs.pingogram.action.CANCEL_TRANSFERS"
    }
}
