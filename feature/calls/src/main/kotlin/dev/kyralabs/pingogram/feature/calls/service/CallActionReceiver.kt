package dev.kyralabs.pingogram.feature.calls.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.feature.calls.CallManager
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Decline" and "Hang up" of the call's notification, and "Leave" of a voice chat's (and of a live stream's
 * picture-in-picture window), which need no screen. Answering opens the call screen instead
 * (`CallActivity`): Android lets the app use the microphone in the background only once it was on screen.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var callManager: CallManager

    @Inject lateinit var groupCallManager: GroupCallManager

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != ACTION_DECLINE && action != ACTION_HANG_UP && action != ACTION_LEAVE) return
        val pending = goAsync()
        scope.launch {
            try {
                if (action == ACTION_LEAVE) groupCallManager.leave() else callManager.hangUp()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "dev.kyralabs.pingogram.calls.DECLINE"
        const val ACTION_HANG_UP = "dev.kyralabs.pingogram.calls.HANG_UP"
        const val ACTION_LEAVE = "dev.kyralabs.pingogram.calls.LEAVE"

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
