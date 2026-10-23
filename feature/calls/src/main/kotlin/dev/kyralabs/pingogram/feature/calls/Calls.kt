package dev.kyralabs.pingogram.feature.calls

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.feature.calls.service.CallNotifications
import dev.kyralabs.pingogram.feature.calls.ui.CallActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the app knows of calls (`docs/ARCHITECTURE.md`, Calls): `PingogramApplication` [start]s them with
 * the app, before TDLib, so incoming calls ring whenever TDLib announces one; the call buttons [call] someone.
 */
@Singleton
class Calls
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val manager: CallManager,
        private val notifications: CallNotifications,
    ) {
        fun start() {
            manager.start()
            notifications.start()
        }

        /** Opens the call screen, which asks for the microphone if needed, then calls user [userId]. */
        fun call(userId: Long) = CallActivity.startCall(context, userId)

        /**
         * Opens the call screen and calls user [userId] with the camera on: a video call
         * (`docs/adr/0019-video-and-group-calls-scope.md`). Without the camera's permission yet, it calls and asks for
         * the camera, which turns on once allowed.
         */
        fun videoCall(userId: Long) = CallActivity.startCall(context, userId, video = true)
    }
