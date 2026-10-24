package dev.kyralabs.pingogram.feature.calls

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.UserProfileRepository
import dev.kyralabs.pingogram.core.ui.component.CallStarter
import dev.kyralabs.pingogram.core.ui.component.VoiceChatSummary
import dev.kyralabs.pingogram.core.ui.component.VoiceChats
import dev.kyralabs.pingogram.feature.calls.service.CallNotifications
import dev.kyralabs.pingogram.feature.calls.service.isOver
import dev.kyralabs.pingogram.feature.calls.ui.CallActivity
import dev.kyralabs.pingogram.feature.calls.ui.CameraAccess
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the app knows of calls (`docs/ARCHITECTURE.md`, Calls): `PingogramApplication` [start]s them with
 * the app, before TDLib, so incoming calls ring whenever TDLib announces one; the call buttons [call] someone, or
 * [videoCall] them where [canVideoCall] says so. It is the [CallStarter] `:app` hands to the features, and their
 * [VoiceChats]: a chat's voice chat, joined or started on the voice chat's screen
 * (`docs/adr/0023-group-voice-chats-and-live-streams.md`).
 */
@Singleton
class Calls
    @Inject
    @Suppress("LongParameterList") // Injected: both managers, the notifications, profiles, the camera, group calls.
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val manager: CallManager,
        private val notifications: CallNotifications,
        private val profiles: UserProfileRepository,
        private val camera: CameraAccess,
        private val groupCalls: GroupCallRepository,
        private val groupCallManager: GroupCallManager,
    ) : CallStarter,
        VoiceChats {
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

        override fun voiceChat(chatId: Long): Flow<VoiceChatSummary?> =
            voiceChatSummary(chatId, groupCalls, groupCallManager.call)

        /** Opens the voice chat's screen, which asks for the microphone if needed, then joins. */
        override fun join(chatId: Long) = GroupCallActivity.open(context, chatId)

        override fun start(chatId: Long) = GroupCallActivity.open(context, chatId, start = true)
    }

/**
 * Chat [chatId]'s voice chat as the chat's screens show it (`Calls.voiceChat`): its group call's count and kind, and
 * whether the user is in it here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun voiceChatSummary(
    chatId: Long,
    groupCalls: GroupCallRepository,
    current: Flow<ActiveGroupCall?>,
): Flow<VoiceChatSummary?> =
    groupCalls
        .videoChat(chatId)
        .map { it?.groupCallId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(groupCalls.groupCall(id), current) { call, active ->
                    VoiceChatSummary(
                        participantCount = call?.participantCount,
                        isLiveStream = call?.isRtmpStream == true,
                        isJoined = active?.groupCallId == id && !active.isOver(),
                    )
                }
            }
        }.distinctUntilChanged()

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
