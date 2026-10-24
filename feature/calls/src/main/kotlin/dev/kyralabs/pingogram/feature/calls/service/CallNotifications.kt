package dev.kyralabs.pingogram.feature.calls.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.notifications.CallNotificationBuilder
import dev.kyralabs.pingogram.feature.calls.ActiveCall
import dev.kyralabs.pingogram.feature.calls.ActiveGroupCall
import dev.kyralabs.pingogram.feature.calls.CallManager
import dev.kyralabs.pingogram.feature.calls.CallPhase
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.GroupCallPhase
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.ui.CallActivity
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the notification of the user's call (`core:notifications`' [CallNotificationBuilder]) as [CallManager.call]
 * changes: ringing while an incoming call rings, then the ongoing call, and none once it ended; and the voice chat's as
 * [GroupCallManager.call] changes (one call at a time, so one notification). It is also the
 * notification of [CallService], which uses the same id, so posting here updates the service's.
 */
@Singleton
class CallNotifications
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val callManager: CallManager,
        private val groupCallManager: GroupCallManager,
        private val builder: CallNotificationBuilder,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        fun start() {
            scope.launch {
                callManager.call
                    .map { it?.let { call -> Shown(call) } }
                    .distinctUntilChanged()
                    .collect { shown ->
                        if (shown == null ||
                            shown.ended
                        ) {
                            // A voice chat may have taken the line (and the notification) meanwhile.
                            if (groupCallManager.call.value.isOver()) manager.cancel(ID)
                        } else {
                            post(build(shown.call))
                        }
                    }
            }
            scope.launch {
                groupCallManager.call
                    .map { it?.let { call -> GroupShown(call) } }
                    .distinctUntilChanged()
                    .collect { shown ->
                        if (shown == null || shown.ended) {
                            if (callManager.call.value.let { it == null || it.phase is CallPhase.Ended }) {
                                manager.cancel(ID)
                            }
                        } else {
                            post(build(shown.call))
                        }
                    }
            }
        }

        /** The notification of voice chat [call] now: where it is, the timer once connected, and "Leave". */
        suspend fun build(call: ActiveGroupCall): Notification =
            builder.voiceChat(
                chatId = call.chatId,
                status = context.getString(call.statusText()),
                connectedAtWallMillis = call.connectedAtMillis?.toWallClock(),
                content = GroupCallActivity.pendingIntent(context, GroupCallActivity.ACTION_SHOW),
                leave = CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_LEAVE),
            )

        /** The notification [call] shows now; [CallService] starts with it. */
        suspend fun build(call: ActiveCall): Notification {
            val content = CallActivity.pendingIntent(context, CallActivity.ACTION_SHOW)
            return if (!call.isOutgoing && call.phase is CallPhase.Ringing) {
                builder.incoming(
                    userId = call.userId,
                    content = content,
                    answer = CallActivity.pendingIntent(context, CallActivity.ACTION_ANSWER),
                    decline = CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_DECLINE),
                    fullScreen = canUseFullScreenIntent(),
                )
            } else {
                builder.ongoing(
                    userId = call.userId,
                    status = context.getString(call.statusText()),
                    connectedAtWallMillis = call.connectedAtMillis?.toWallClock(),
                    content = content,
                    hangUp = CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_HANG_UP),
                )
            }
        }

        /**
         * Android 14+ lets apps use full-screen intents only with the user's consent (`USE_FULL_SCREEN_INTENT`, which
         * calling apps get by default); without it the incoming call shows as a heads-up notification.
         */
        private fun canUseFullScreenIntent(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        @SuppressLint("MissingPermission") // Checked first: without it Android drops the notification anyway.
        private fun post(notification: Notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.notify(ID, notification)
        }

        /** What the notification depends on; the call's other changes (the audio route, the emojis) leave it be. */
        private data class Shown(
            val userId: Long,
            val isOutgoing: Boolean,
            val phase: Any,
            val isOnHold: Boolean,
            val connectedAtMillis: Long?,
        ) {
            /** The call it shows, as last seen: what [build] reads. */
            lateinit var call: ActiveCall

            val ended get() = phase == CallPhase.Ended::class

            companion object {
                operator fun invoke(call: ActiveCall) =
                    Shown(
                        call.userId,
                        call.isOutgoing,
                        // Ringing with whether it reached the device; other phases by kind (an end's reason aside).
                        if (call.phase is CallPhase.Ringing) call.phase else call.phase::class,
                        call.isOnHold,
                        call.connectedAtMillis,
                    ).also { it.call = call }
            }
        }

        /** What a voice chat's notification depends on: its phase by kind, its start and whether it streams. */
        private data class GroupShown(
            val chatId: Long,
            val phase: Any,
            val connectedAtMillis: Long?,
            val isStream: Boolean,
        ) {
            lateinit var call: ActiveGroupCall

            val ended get() = phase == GroupCallPhase.Ended::class

            companion object {
                operator fun invoke(call: ActiveGroupCall) =
                    GroupShown(call.chatId, call.phase::class, call.connectedAtMillis, call.isStream).also {
                        it.call = call
                    }
            }
        }

        internal companion object {
            /** The notification id of the call, the foreground service's too. */
            const val ID = 0x5ca11
        }
    }

/** The call's state in a few words, for the ongoing notification. */
internal fun ActiveCall.statusText(): Int =
    when (val phase = phase) {
        CallPhase.Requesting -> {
            R.string.call_status_requesting
        }

        is CallPhase.Ringing -> {
            if (isOutgoing &&
                phase.reachedDevice
            ) {
                R.string.call_status_ringing
            } else {
                R.string.call_status_calling
            }
        }

        CallPhase.Connecting -> {
            R.string.call_status_connecting
        }

        CallPhase.Connected -> {
            if (isOnHold) R.string.call_status_on_hold else R.string.call_status_connected
        }

        CallPhase.Reconnecting -> {
            R.string.call_status_reconnecting
        }

        is CallPhase.Ended -> {
            R.string.call_status_ended
        }
    }

/** A voice chat's state in a few words, for its notification. */
internal fun ActiveGroupCall.statusText(): Int =
    when (phase) {
        GroupCallPhase.Joining -> R.string.group_call_joining
        GroupCallPhase.Connected -> if (isStream) R.string.group_call_status_stream else R.string.voice_chat
        GroupCallPhase.Reconnecting -> R.string.call_status_reconnecting
        is GroupCallPhase.Ended -> R.string.call_status_ended
    }

/** Whether no voice chat is under way: none, or one that ended. */
internal fun ActiveGroupCall?.isOver(): Boolean = this == null || phase is GroupCallPhase.Ended

/** A time on the elapsed-realtime clock as the wall-clock time it was, for the notification's chronometer. */
private fun Long.toWallClock(): Long = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - this)
