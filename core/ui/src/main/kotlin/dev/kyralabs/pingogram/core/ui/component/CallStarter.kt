package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow

/**
 * Calls a user (`REQUIREMENTS.md` FR-V1): what the call buttons of the conversation and the profile ([CallButtonMenu])
 * and a call's service message ("Call back") do. `:app` provides it from `feature:calls`, which features do not
 * depend on, as links go through `LocalUriHandler`.
 */
interface CallStarter {
    /** Opens the call screen and calls user [userId], audio only. */
    fun call(userId: Long)

    /** Opens the call screen and calls user [userId] with the camera on. */
    fun videoCall(userId: Long)

    /**
     * Whether user [userId] can get a video call from this device, then again after every change: the device has a
     * camera, and TDLib's full info says the user can be called and their app takes video calls (td_api.tl
     * `userFullInfo.can_be_called`, `supports_video_calls`). False until known, and if the full info fails.
     */
    fun canVideoCall(userId: Long): Flow<Boolean>
}

/** The app's [CallStarter]; null in previews and tests, where the call buttons are left out. */
val LocalCallStarter = staticCompositionLocalOf<CallStarter?> { null }
