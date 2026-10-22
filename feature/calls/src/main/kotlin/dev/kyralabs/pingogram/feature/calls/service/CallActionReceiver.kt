package dev.kyralabs.pingogram.feature.calls.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.feature.calls.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Decline" and "Hang up" of the call's notification, which need no screen. Answering opens the call screen instead
 * (`CallActivity`): Android lets the app use the microphone in the background only once it was on screen.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var callManager: CallManager

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_DECLINE && intent.action != ACTION_HANG_UP) return
        val pending = goAsync()
        scope.launch {
            try {
                callManager.hangUp()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "dev.kyralabs.pingogram.calls.DECLINE"
        const val ACTION_HANG_UP = "dev.kyralabs.pingogram.calls.HANG_UP"

        fun pendingIntent(
            context: Context,
            action: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, CallActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
