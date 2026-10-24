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
 * "Decline", "Hang up" and "Stop sharing" of the call's notification, and "Leave" of a voice chat's (and of a live
 * stream's picture-in-picture window), which need no screen. Answering opens the call screen instead
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
        if (action !in ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_LEAVE -> groupCallManager.leave()
                    ACTION_STOP_SHARING -> callManager.stopScreenSharing()
                    else -> callManager.hangUp()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "dev.kyralabs.pingogram.calls.DECLINE"
        const val ACTION_HANG_UP = "dev.kyralabs.pingogram.calls.HANG_UP"
        const val ACTION_LEAVE = "dev.kyralabs.pingogram.calls.LEAVE"
        const val ACTION_STOP_SHARING = "dev.kyralabs.pingogram.calls.STOP_SHARING"
        private val ACTIONS = setOf(ACTION_DECLINE, ACTION_HANG_UP, ACTION_LEAVE, ACTION_STOP_SHARING)

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
