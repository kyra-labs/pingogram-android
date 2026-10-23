package dev.kyralabs.pingogram.feature.calls

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.UserProfileRepository
import dev.kyralabs.pingogram.core.ui.component.CallStarter
import dev.kyralabs.pingogram.feature.calls.service.CallNotifications
import dev.kyralabs.pingogram.feature.calls.ui.CallActivity
import dev.kyralabs.pingogram.feature.calls.ui.CameraAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the app knows of calls (`docs/ARCHITECTURE.md`, Calls): `PingogramApplication` [start]s them with
 * the app, before TDLib, so incoming calls ring whenever TDLib announces one; the call buttons [call] someone, or
 * [videoCall] them where [canVideoCall] says so. It is the [CallStarter] `:app` hands to the features.
 */
@Singleton
class Calls
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val manager: CallManager,
        private val notifications: CallNotifications,
        private val profiles: UserProfileRepository,
        private val camera: CameraAccess,
    ) : CallStarter {
        fun start() {
            manager.start()
            notifications.start()
        }

        /** Opens the call screen, which asks for the microphone if needed, then calls user [userId]. */
        override fun call(userId: Long) = CallActivity.startCall(context, userId)

        /**
         * Opens the call screen and calls user [userId] with the camera on: a video call
         * (`docs/adr/0019-video-and-group-calls-scope.md`). Without the camera's permission yet, it calls and asks for
         * the camera, which turns on once allowed.
         */
        override fun videoCall(userId: Long) = CallActivity.startCall(context, userId, video = true)

        override fun canVideoCall(userId: Long): Flow<Boolean> = videoCallable(userId, profiles, camera)
    }

/**
 * Whether user [userId] can get a video call (`Calls.canVideoCall`): the device has a camera, and the user's full
 * info says they can be called and take video calls. A device without a camera asks TDLib nothing.
 */
internal fun videoCallable(
    userId: Long,
    profiles: UserProfileRepository,
    camera: CameraAccess,
): Flow<Boolean> =
    if (!camera.hasCamera()) {
        flowOf(false)
    } else {
        profiles
            .fullInfo(userId)
            .map { info -> info is Outcome.Success && info.value.canBeCalled && info.value.supportsVideoCalls }
            .distinctUntilChanged()
    }
