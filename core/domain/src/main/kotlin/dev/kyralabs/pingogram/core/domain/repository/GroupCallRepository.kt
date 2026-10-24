package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Voice chats and live streams as TDLib signals them (`docs/adr/0023-group-voice-chats-and-live-streams.md`,
 * `docs/ARCHITECTURE.md`, Calls). TDLib announces a chat's voice chat with the chat (`chat.video_chat`,
 * `updateChatVideoChat`), group calls with `updateGroupCall` and the participants of the call the user is in with
 * `updateGroupCallParticipant`; none is replayed, so this repository subscribes at app start. Everything is dropped
 * when TDLib closes (logout).
 *
 * The actions fail with a `DomainError`: `AdminRightsRequired` where the user may not, `FloodWait`, `Network`, …
 */
@Suppress("TooManyFunctions") // One function per td_api.tl group call request a voice chat or a live stream needs.
interface GroupCallRepository {
    /** Chat [chatId]'s voice chat, then every change; null while the chat has none. */
    fun videoChat(chatId: Long): Flow<ChatVideoChat?>

    /**
     * Group call [id], then every change; fetched once (`getGroupCall`) when TDLib has not announced it yet. Null
     * until it is known, and if it cannot be fetched.
     */
    fun groupCall(id: Int): Flow<GroupCall?>

    /**
     * The participants of group call [id] TDLib announced, in TDLib's order (speakers and raised hands first); TDLib
     * announces them only while the user is in the call, and more with [loadParticipants].
     */
    fun participants(id: Int): Flow<ImmutableList<GroupCallParticipant>>

    /** Asks TDLib to announce up to [limit] more participants of call [id]. */
    suspend fun loadParticipants(
        id: Int,
        limit: Int,
    ): Outcome<Unit>

    /** Starts a voice chat in chat [chatId] now, as an administrator who may manage them; returns its call's id. */
    suspend fun startVideoChat(chatId: Long): Outcome<Int>

    /**
     * Joins group call [id] as [participant] (null: TDLib's default) with the engine's join [payload] (JSON) and its
     * microphone's [audioSourceId], [isMuted] or not; returns the call's connection parameters for the engine (JSON).
     */
    suspend fun join(
        id: Int,
        participant: MessageSender?,
        audioSourceId: Int,
        payload: String,
        isMuted: Boolean,
    ): Outcome<String>

    /** Leaves group call [id]; it goes on for the others. */
    suspend fun leave(id: Int): Outcome<Unit>

    /** Ends group call [id] for everyone, as one who may manage it. */
    suspend fun end(id: Int): Outcome<Unit>

    /** Mutes or unmutes [participant] in call [id]: the user themselves, or others for everyone as an administrator. */
    suspend fun setMuted(
        id: Int,
        participant: MessageSender,
        isMuted: Boolean,
    ): Outcome<Unit>

    /**
     * Tells the others in call [id] that the user sends their camera, or stopped ([enabled] false)
     * (`toggleGroupCallIsMyVideoEnabled`); the engine sends or stops the frames.
     */
    suspend fun setMyVideoEnabled(
        id: Int,
        enabled: Boolean,
    ): Outcome<Unit>

    /**
     * Starts sharing the user's screen in call [id] (`startGroupCallScreenSharing`, `docs/adr/0026-screen-sharing.md`)
     * with the engine's presentation [payload] (JSON) and its [audioSourceId]; returns the presentation's connection
     * parameters for the engine (JSON).
     */
    suspend fun startScreenSharing(
        id: Int,
        audioSourceId: Int,
        payload: String,
    ): Outcome<String>

    /** Stops sharing the user's screen in call [id] (`endGroupCallScreenSharing`). */
    suspend fun endScreenSharing(id: Int): Outcome<Unit>

    /** Raises or lowers [participant]'s hand in call [id]: the user's own, or lowers others' as an administrator. */
    suspend fun setHandRaised(
        id: Int,
        participant: MessageSender,
        isHandRaised: Boolean,
    ): Outcome<Unit>

    /** The channels of live stream [id], each with its newest segment's time. */
    suspend fun streams(id: Int): Outcome<ImmutableList<GroupCallStream>>

    /**
     * The segment of live stream [id] that starts at [timeOffset] (ms) on [channelId] at [scale], a video channel's
     * in [quality] (null: the lowest Telegram has), as the engine takes it.
     */
    suspend fun streamSegment(
        id: Int,
        timeOffset: Long,
        scale: Int,
        channelId: Int,
        quality: GroupCallVideoQuality?,
    ): StreamSegment
}
