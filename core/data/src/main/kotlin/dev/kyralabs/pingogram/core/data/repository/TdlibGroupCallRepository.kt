package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toChatVideoChat
import dev.kyralabs.pingogram.core.data.mapper.toGroupCall
import dev.kyralabs.pingogram.core.data.mapper.toGroupCallParticipant
import dev.kyralabs.pingogram.core.data.mapper.toGroupCallStream
import dev.kyralabs.pingogram.core.data.mapper.toTdApi
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GroupCallRepository] on TDLib's voice chats. TDLib announces a chat's voice chat with the chat (`updateNewChat`,
 * then `updateChatVideoChat`), each group call with `updateGroupCall` (the whole call at each change) and the
 * participants of the call the user is in with `updateGroupCallParticipant` (an empty `order`: gone from the list), so
 * this cache keeps the chats' voice chats (a handful), the calls TDLib told about and the participants of the call the
 * user is in.
 */
@Singleton
@Suppress("TooManyFunctions") // One function per td_api.tl request of GroupCallRepository.
class TdlibGroupCallRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : GroupCallRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, GroupCallState(), ::reduce)

        override fun videoChat(chatId: Long): Flow<ChatVideoChat?> =
            store.state.map { it.videoChats[chatId] }.distinctUntilChanged()

        override fun groupCall(id: Int): Flow<GroupCall?> =
            store.state
                .map { it.calls[id] }
                .distinctUntilChanged()
                .onStart {
                    if (store.state.value.calls[id] == null) {
                        val fetched = client.request(TdApi.GetGroupCall(id))
                        if (fetched is Outcome.Success) {
                            val call = fetched.value.toGroupCall()
                            // An update that came meanwhile is newer than the answer.
                            store.update { state ->
                                if (state.calls[id] != null) state else state.copy(calls = state.calls.put(id, call))
                            }
                        }
                    }
                }

        override fun participants(id: Int): Flow<ImmutableList<GroupCallParticipant>> =
            store.state
                .map { it.participants[id] }
                .distinctUntilChanged { old, new -> old === new }
                .map { participants ->
                    participants
                        ?.values
                        .orEmpty()
                        // td_api.tl: orders are compared lexicographically, the bigger the higher.
                        .sortedByDescending { it.order }
                        .toImmutableList()
                }

        override suspend fun loadParticipants(
            id: Int,
            limit: Int,
        ): Outcome<Unit> = client.request(TdApi.LoadGroupCallParticipants(id, limit)).map {}

        override suspend fun startVideoChat(chatId: Long): Outcome<Int> =
            // No title (the chat's stands in), now rather than scheduled, a voice chat rather than an RTMP stream.
            client.request(TdApi.CreateVideoChat(chatId, "", 0, false)).map { it.id }

        override suspend fun join(
            id: Int,
            participant: MessageSender?,
            audioSourceId: Int,
            payload: String,
            isMuted: Boolean,
        ): Outcome<String> =
            client
                .request(
                    TdApi.JoinVideoChat(
                        id,
                        participant?.toTdMessageSender(),
                        // No video: group video is not offered (docs/adr/0023-group-voice-chats-and-live-streams.md).
                        TdApi.GroupCallJoinParameters(audioSourceId, payload, isMuted, false),
                        // invite_hash: only for joining through a voice chat's invite link.
                        "",
                    ),
                ).map { it.text.orEmpty() }

        override suspend fun leave(id: Int): Outcome<Unit> =
            client.request(TdApi.LeaveGroupCall(id)).map {
                // TDLib announces no participant of a call the user left: the list would go stale.
                store.update { state -> state.copy(participants = state.participants.remove(id)) }
            }

        override suspend fun end(id: Int): Outcome<Unit> = client.request(TdApi.EndGroupCall(id)).map {}

        override suspend fun setMuted(
            id: Int,
            participant: MessageSender,
            isMuted: Boolean,
        ): Outcome<Unit> =
            client
                .request(TdApi.ToggleGroupCallParticipantIsMuted(id, participant.toTdMessageSender(), isMuted))
                .map {}

        override suspend fun setMyVideoEnabled(
            id: Int,
            enabled: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleGroupCallIsMyVideoEnabled(id, enabled)).map {}

        override suspend fun setHandRaised(
            id: Int,
            participant: MessageSender,
            isHandRaised: Boolean,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.ToggleGroupCallParticipantIsHandRaised(id, participant.toTdMessageSender(), isHandRaised),
                ).map {}

        override suspend fun streams(id: Int): Outcome<ImmutableList<GroupCallStream>> =
            client.request(TdApi.GetGroupCallStreams(id)).map { answer ->
                answer.streams
                    .orEmpty()
                    .map { it.toGroupCallStream() }
                    .toImmutableList()
            }

        override suspend fun streamSegment(
            id: Int,
            timeOffset: Long,
            scale: Int,
            channelId: Int,
            quality: GroupCallVideoQuality?,
        ): StreamSegment {
            val function = TdApi.GetGroupCallStreamSegment(id, timeOffset, scale, channelId, quality?.toTdApi())
            // Raw, for the error's text: Telegram's TIME_TOO_BIG means "not recorded yet", as its apps read it.
            val answer = withTimeoutOrNull(REQUEST_TIMEOUT) { client.send(function) } ?: return StreamSegment.NotReady
            return when (answer) {
                is TdResult.Success -> {
                    StreamSegment.Data(answer.value.data ?: ByteArray(0))
                }

                is TdResult.Failure -> {
                    val error = answer.error
                    if (error.message == "TIME_TOO_BIG" || error.retryAfter != null) {
                        StreamSegment.NotReady
                    } else {
                        StreamSegment.Unavailable
                    }
                }
            }
        }
    }

/** What [TdlibGroupCallRepository] keeps: chats' voice chats, the group calls and the joined call's participants. */
internal data class GroupCallState(
    val videoChats: PersistentMap<Long, ChatVideoChat> = persistentMapOf(),
    val calls: PersistentMap<Int, GroupCall> = persistentMapOf(),
    val participants: PersistentMap<Int, PersistentMap<MessageSender, GroupCallParticipant>> = persistentMapOf(),
)

/** The state with [update] applied; the same instance for other updates and for what is unchanged. */
private fun reduce(
    state: GroupCallState,
    update: TdApi.Update,
): GroupCallState =
    when (update) {
        is TdApi.UpdateNewChat -> {
            state.withVideoChat(update.chat.id, update.chat.videoChat?.toChatVideoChat())
        }

        is TdApi.UpdateChatVideoChat -> {
            state.withVideoChat(update.chatId, update.videoChat?.toChatVideoChat())
        }

        is TdApi.UpdateGroupCall -> {
            val call = update.groupCall.toGroupCall()
            when {
                state.calls[call.id] == call -> {
                    state
                }

                // An ended call has no one left to list.
                !call.isActive && call.scheduledStartDate == 0L -> {
                    state.copy(
                        calls = state.calls.put(call.id, call),
                        participants = state.participants.remove(call.id),
                    )
                }

                else -> {
                    state.copy(calls = state.calls.put(call.id, call))
                }
            }
        }

        is TdApi.UpdateGroupCallParticipant -> {
            state.withParticipant(update.groupCallId, update.participant)
        }

        else -> {
            state
        }
    }

private fun GroupCallState.withVideoChat(
    chatId: Long,
    videoChat: ChatVideoChat?,
): GroupCallState =
    when {
        videoChats[chatId] == videoChat -> this
        videoChat == null -> copy(videoChats = videoChats.remove(chatId))
        else -> copy(videoChats = videoChats.put(chatId, videoChat))
    }

private fun GroupCallState.withParticipant(
    callId: Int,
    participant: TdApi.GroupCallParticipant,
): GroupCallState {
    val mapped = participant.toGroupCallParticipant()
    val current = participants[callId] ?: persistentMapOf()
    val next =
        when {
            mapped == null || current[mapped.participant] == mapped -> current
            mapped.order.isEmpty() -> current.remove(mapped.participant)
            else -> current.put(mapped.participant, mapped)
        }
    return when {
        next === current -> this
        next.isEmpty() -> copy(participants = participants.remove(callId))
        else -> copy(participants = participants.put(callId, next))
    }
}
