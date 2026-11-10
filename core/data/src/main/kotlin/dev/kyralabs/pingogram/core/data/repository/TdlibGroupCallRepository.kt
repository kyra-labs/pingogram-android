package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.error.toDomainError
import dev.kyralabs.pingogram.core.data.mapper.toChatVideoChat
import dev.kyralabs.pingogram.core.data.mapper.toGroupCall
import dev.kyralabs.pingogram.core.data.mapper.toGroupCallComment
import dev.kyralabs.pingogram.core.data.mapper.toGroupCallParticipant
import dev.kyralabs.pingogram.core.data.mapper.toGroupCallStream
import dev.kyralabs.pingogram.core.data.mapper.toTdApi
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTdMessageSender
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallComment
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.GroupCallComments
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.MAX_COMMENTS
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
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

/**
 * [GroupCallRepository] on TDLib's voice chats. TDLib announces a chat's voice chat with the chat (`updateNewChat`,
 * then `updateChatVideoChat`), each group call with `updateGroupCall` (the whole call at each change) and the
 * participants of the call the user is in with `updateGroupCallParticipant` (an empty `order`: gone from the list), so
 * this cache keeps the chats' voice chats (a handful), the calls TDLib told about and the participants of the call the
 * user is in. Comments come with `updateNewGroupCallMessage` only (TDLib keeps none), so it keeps the newest
 * [MAX_COMMENTS] of each call the user is in.
 */
@AccountScoped
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
                // TDLib announces no participant or comment of a call the user left: both would go stale.
                store.update { state ->
                    state.copy(participants = state.participants.remove(id), comments = state.comments.remove(id))
                }
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

        override suspend fun startScreenSharing(
            id: Int,
            audioSourceId: Int,
            payload: String,
        ): Outcome<String> =
            client.request(TdApi.StartGroupCallScreenSharing(id, audioSourceId, payload)).map { it.text }

        override suspend fun endScreenSharing(id: Int): Outcome<Unit> =
            client.request(TdApi.EndGroupCallScreenSharing(id)).map {}

        override suspend fun setHandRaised(
            id: Int,
            participant: MessageSender,
            isHandRaised: Boolean,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.ToggleGroupCallParticipantIsHandRaised(id, participant.toTdMessageSender(), isHandRaised),
                ).map {}

        override fun comments(id: Int): Flow<GroupCallComments> =
            store.state
                .map { it.comments[id] }
                .distinctUntilChanged { old, new -> old === new }
                .map { it?.toComments() ?: GroupCallComments() }

        override suspend fun sendComment(
            id: Int,
            text: FormattedText,
        ): Outcome<Unit> =
            // No Stars: Pingogram never pays (CLAUDE.md rule 3); the screen does not offer paid comments.
            client.request(TdApi.SendGroupCallMessage(id, text.toTdFormattedText(), 0)).map {}

        override suspend fun deleteComments(
            id: Int,
            commentIds: List<Int>,
            reportSpam: Boolean,
        ): Outcome<Unit> = client.request(TdApi.DeleteGroupCallMessages(id, commentIds.toIntArray(), reportSpam)).map {}

        override suspend fun deleteCommentsBy(
            id: Int,
            sender: MessageSender,
            reportSpam: Boolean,
        ): Outcome<Unit> =
            client
                .request(TdApi.DeleteGroupCallMessagesBySender(id, sender.toTdMessageSender(), reportSpam))
                .map {}

        override suspend fun setCommentsAllowed(
            id: Int,
            allowed: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleGroupCallAreMessagesAllowed(id, allowed)).map {}

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

/** A call's comments as [TdlibGroupCallRepository] keeps them: the newest last, and how many came. */
internal data class CommentLog(
    val items: PersistentList<GroupCallComment> = persistentListOf(),
    val received: Int = 0,
) {
    fun toComments() = GroupCallComments(items, received)
}

/** What [TdlibGroupCallRepository] keeps: chats' voice chats, the group calls and the joined call's participants. */
internal data class GroupCallState(
    val videoChats: PersistentMap<Long, ChatVideoChat> = persistentMapOf(),
    val calls: PersistentMap<Int, GroupCall> = persistentMapOf(),
    val participants: PersistentMap<Int, PersistentMap<MessageSender, GroupCallParticipant>> = persistentMapOf(),
    val comments: PersistentMap<Int, CommentLog> = persistentMapOf(),
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

                // An ended call has no one left to list, nor comments to show.
                !call.isActive && call.scheduledStartDate == 0L -> {
                    state.copy(
                        calls = state.calls.put(call.id, call),
                        participants = state.participants.remove(call.id),
                        comments = state.comments.remove(call.id),
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

        is TdApi.UpdateNewGroupCallMessage -> {
            update.message.toGroupCallComment()?.let { state.withComment(update.groupCallId, it) } ?: state
        }

        is TdApi.UpdateGroupCallMessagesDeleted -> {
            state.withoutComments(update.groupCallId, update.messageIds.toSet())
        }

        is TdApi.UpdateGroupCallMessageSendFailed -> {
            state.withFailedComment(update.groupCallId, update.messageId, update.error)
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

private fun GroupCallState.withComment(
    callId: Int,
    comment: GroupCallComment,
): GroupCallState {
    val log = comments[callId] ?: CommentLog()
    val index = log.items.indexOfFirst { it.id == comment.id }
    val items =
        when {
            index >= 0 -> log.items.set(index, comment)
            log.items.size >= MAX_COMMENTS -> log.items.removeAt(0).add(comment)
            else -> log.items.add(comment)
        }
    val received = if (index >= 0) log.received else log.received + 1
    return copy(comments = comments.put(callId, CommentLog(items, received)))
}

private fun GroupCallState.withoutComments(
    callId: Int,
    ids: Set<Int>,
): GroupCallState {
    val log = comments[callId] ?: return this
    val items = log.items.removeAll { it.id in ids }
    return if (items === log.items) this else copy(comments = comments.put(callId, log.copy(items = items)))
}

/** The user's comment Telegram refused stays, marked, so its text is not lost without a word. */
private fun GroupCallState.withFailedComment(
    callId: Int,
    commentId: Int,
    error: TdApi.Error?,
): GroupCallState {
    val log = comments[callId] ?: return this
    val index = log.items.indexOfFirst { it.id == commentId }
    if (index < 0) return this
    val failure = TdError(error?.code ?: 0, error?.message.orEmpty()).toDomainError()
    val items = log.items.set(index, log.items[index].copy(sendError = failure))
    return copy(comments = comments.put(callId, log.copy(items = items)))
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
