package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import kotlin.random.Random

/**
 * Scripted calls in the demo (`docs/adr/0034-demo-mode-for-store-review.md`): the other side of TDLib's signaling,
 * played on the phone. A call to a sample person rings after a second and is answered a few seconds later, with a
 * made-up key and its four emojis; a sample person calls the user when asked ([DemoIncomingCall]). The book club's
 * voice chat goes on with sample people, who speak in turn once the user joins. Nothing goes to Telegram, and the
 * app's demo engine connects to nothing: the media stay on the phone. Each call ends in the chat as Telegram records
 * it, and its record is replayed with the journal.
 *
 * One branch per function, and guard clauses for calls that are not the demo's: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions", "ReturnCount", "LargeClass")
internal class DemoCalls : DemoArea {
    private class OneToOne(
        val call: TdApi.Call,
        var accepted: Boolean = false,
        var readyAt: Int = 0,
    )

    private class VoiceChat(
        val chatId: Long,
        val call: TdApi.GroupCall,
        val participants: LinkedHashMap<Long, TdApi.GroupCallParticipant>,
    )

    private val calls = mutableMapOf<Int, OneToOne>()
    private val voiceChats = mutableMapOf<Int, VoiceChat>()
    private var nextCallId = 1
    private var nextVoiceChatId = DemoData.SAMPLE_VOICE_CHAT + 1

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? {
        seed(world)
        return when (function) {
            is TdApi.CreateCall -> {
                createCall(world, function)
            }

            is DemoIncomingCall -> {
                incomingCall(world, function.userId)
            }

            is TdApi.AcceptCall -> {
                acceptCall(function.callId, function.protocol)
            }

            is TdApi.DiscardCall -> {
                discardCall(world, function)
            }

            is TdApi.SendCallSignalingData, is TdApi.SendCallRating -> {
                ok(TdApi.Ok())
            }

            is TdApi.SearchCallMessages -> {
                callMessages(world, function)
            }

            is TdApi.GetGroupCall -> {
                voiceChats[function.groupCallId]?.let { ok(it.call.snapshot()) } ?: notFound()
            }

            is TdApi.CreateVideoChat -> {
                createVoiceChat(world, function.chatId, function.title)
            }

            is TdApi.JoinVideoChat -> {
                join(world, function)
            }

            is TdApi.LoadGroupCallParticipants -> {
                val voiceChat = voiceChats[function.groupCallId] ?: return notFound()
                if (!voiceChat.call.isJoined) return badRequest("GROUPCALL_JOIN_MISSING")
                DemoAnswer(ok(TdApi.Ok()).result, participantUpdates(voiceChat))
            }

            is TdApi.ToggleGroupCallParticipantIsMuted -> {
                changeParticipant(function.groupCallId, function.participantId) {
                    isMutedForAllUsers = function.isMuted
                    if (function.isMuted) isSpeaking = false
                }
            }

            is TdApi.ToggleGroupCallParticipantIsHandRaised -> {
                changeParticipant(function.groupCallId, function.participantId) { isHandRaised = function.isHandRaised }
            }

            is TdApi.ToggleGroupCallIsMyVideoEnabled -> {
                changeCall(function.groupCallId) { isMyVideoEnabled = function.isMyVideoEnabled }
            }

            is TdApi.StartGroupCallScreenSharing -> {
                voiceChats[function.groupCallId]?.let { ok(TdApi.Text(EMPTY_JSON)) } ?: notFound()
            }

            is TdApi.EndGroupCallScreenSharing -> {
                voiceChats[function.groupCallId]?.let { ok(TdApi.Ok()) } ?: notFound()
            }

            is TdApi.LeaveGroupCall -> {
                leave(function.groupCallId)
            }

            is TdApi.EndGroupCall -> {
                end(world, function.groupCallId)
            }

            else -> {
                null
            }
        }
    }

    /** The voice chats that go on, as TDLib announces them when asked; the book club's first. */
    fun announce(world: DemoWorld): List<TdApi.Update> {
        seed(world)
        return voiceChats.values.filter { it.call.isActive }.map { TdApi.UpdateGroupCall(it.call.snapshot()) }
    }

    private fun seed(world: DemoWorld) {
        if (voiceChats.isNotEmpty() || nextVoiceChatId > DemoData.SAMPLE_VOICE_CHAT + 1) return
        val call = groupCall(DemoData.SAMPLE_VOICE_CHAT, world.data.text(R.string.demo_voice_chat_title))
        val people =
            listOf(DemoData.ADA, DemoData.BEN, DemoData.CHLOE).mapIndexed { index, userId ->
                userId to
                    participant(userId, order = index + 1).apply {
                        isMutedForAllUsers = userId == DemoData.BEN
                        isHandRaised = userId == DemoData.CHLOE
                    }
            }
        voiceChats[call.id] = VoiceChat(DemoData.FORUM_CHAT, call, LinkedHashMap(people.toMap()))
        call.participantCount = people.size
    }

    private fun createCall(
        world: DemoWorld,
        function: TdApi.CreateCall,
    ): DemoAnswer {
        val user = world.users[function.userId] ?: return notFound()
        if (user.id == DemoData.ME || user.type !is TdApi.UserTypeRegular) return badRequest("USER_ID_INVALID")
        val id = nextCallId++
        val call = TdApi.Call(id, id.toLong(), user.id, true, function.isVideo, TdApi.CallStatePending(true, false))
        calls[id] = OneToOne(call)
        val ringing =
            DemoLater(RING_AFTER) {
                stillPending(id)
                    ?.let { pending ->
                        pending.call.state = TdApi.CallStatePending(true, true)
                        listOf(TdApi.UpdateCall(pending.call.snapshot()))
                    }.orEmpty()
            }
        val answered =
            DemoLater(ANSWER_AFTER) { now ->
                stillPending(id)
                    ?.let { pending ->
                        pending.accepted = true
                        connect(pending, function.protocol, now.now)
                    }.orEmpty()
            }
        return changed(TdApi.CallId(id), listOf(TdApi.UpdateCall(call.snapshot()))).then(listOf(ringing, answered))
    }

    private fun incomingCall(
        world: DemoWorld,
        userId: Long,
    ): DemoAnswer {
        val user = world.users[userId] ?: return notFound()
        if (user.id == DemoData.ME || user.type !is TdApi.UserTypeRegular) return badRequest("USER_ID_INVALID")
        val id = nextCallId++
        val call = TdApi.Call(id, id.toLong(), userId, false, false, TdApi.CallStatePending(true, true))
        calls[id] = OneToOne(call)
        val rings =
            DemoLater(
                INCOMING_AFTER,
            ) { listOf(TdApi.UpdateCall(call.snapshot())).takeIf { stillPending(id) != null }.orEmpty() }
        return okChanged(emptyList()).then(listOf(rings))
    }

    private fun acceptCall(
        callId: Int,
        protocol: TdApi.CallProtocol,
    ): DemoAnswer {
        val incoming = calls[callId]?.takeIf { !it.call.isOutgoing } ?: return badRequest("CALL_ID_INVALID")
        incoming.accepted = true
        incoming.call.state = TdApi.CallStateExchangingKeys()
        val ready =
            DemoLater(KEYS_AFTER) { world ->
                calls[callId]
                    ?.takeIf { it.call.state is TdApi.CallStateExchangingKeys }
                    ?.let { connect(it, protocol, world.now) }
                    .orEmpty()
            }
        return okChanged(listOf(TdApi.UpdateCall(incoming.call.snapshot()))).then(listOf(ready))
    }

    /** The key is agreed: a made-up one, and its four emojis, which the call screen shows to compare. */
    private fun connect(
        call: OneToOne,
        protocol: TdApi.CallProtocol,
        now: Int,
    ): List<TdApi.Update> {
        call.readyAt = now
        val random = Random(call.call.id * KEY_SEED + call.call.userId)
        call.call.state =
            TdApi.CallStateReady(
                protocol,
                emptyArray(),
                EMPTY_JSON,
                random.nextBytes(KEY_SIZE),
                Array(EMOJI_COUNT) { EMOJIS[random.nextInt(EMOJIS.size)] },
                false,
                false,
                EMPTY_JSON,
            )
        return listOf(TdApi.UpdateCall(call.call.snapshot()))
    }

    private fun stillPending(id: Int): OneToOne? = calls[id]?.takeIf { it.call.state is TdApi.CallStatePending }

    /** The call ends: as hung up once answered, declined or cancelled before, recorded in the chat as Telegram does. */
    private fun discardCall(
        world: DemoWorld,
        function: TdApi.DiscardCall,
    ): DemoAnswer {
        val record = calls[function.callId] ?: return ok(TdApi.Ok())
        val call = record.call
        if (call.state is TdApi.CallStateDiscarded) return ok(TdApi.Ok())
        val reason: TdApi.CallDiscardReason =
            when {
                record.accepted -> TdApi.CallDiscardReasonHungUp()
                call.isOutgoing -> TdApi.CallDiscardReasonMissed()
                else -> TdApi.CallDiscardReasonDeclined()
            }
        call.state = TdApi.CallStateDiscarded(reason, false, false, false)
        val duration = if (record.accepted) function.duration else 0
        val content = TdApi.MessageCall(call.uniqueId, call.isVideo, reason, duration)
        val updates = mutableListOf<TdApi.Update>(TdApi.UpdateCall(call.snapshot()))
        updates += world.privateChat(call.userId)?.second.orEmpty()
        val recorded =
            if (call.isOutgoing) {
                world.addOwn(call.userId, content)
            } else {
                world.addIncoming(call.userId, call.userId, content)
            }
        updates += recorded?.second.orEmpty()
        return okChanged(updates)
    }

    /** The calls in every chat, newest first, as `searchCallMessages` pages them (its offset: how many came before). */
    private fun callMessages(
        world: DemoWorld,
        function: TdApi.SearchCallMessages,
    ): DemoAnswer {
        val found =
            world
                .allChats()
                .flatMap { world.messagesOf(it.id) }
                .filter { message ->
                    val call = message.content as? TdApi.MessageCall ?: return@filter false
                    !function.onlyMissed ||
                        (!message.isOutgoing && call.discardReason !is TdApi.CallDiscardReasonHungUp)
                }.sortedByDescending { it.date }
        val from = function.offset.toIntOrNull() ?: 0
        val page = found.drop(from).take(function.limit)
        val next = if (from + page.size < found.size) (from + page.size).toString() else ""
        return ok(TdApi.FoundMessages(found.size, page.toTypedArray(), next))
    }

    private fun createVoiceChat(
        world: DemoWorld,
        chatId: Long,
        title: String,
    ): DemoAnswer {
        val chat = world.chat(chatId) ?: return notFound()
        if (world.groups.statusOf(chatId) !is TdApi.ChatMemberStatusCreator) return badRequest("CHAT_ADMIN_REQUIRED")
        voiceChats.values
            .firstOrNull {
                it.chatId == chatId && it.call.isActive
            }?.let { return ok(TdApi.GroupCallId(it.call.id)) }
        val call = groupCall(nextVoiceChatId++, title)
        voiceChats[call.id] = VoiceChat(chatId, call, linkedMapOf())
        chat.videoChat = TdApi.VideoChat(call.id, false, TdApi.MessageSenderUser(DemoData.ME))
        return changed(
            TdApi.GroupCallId(call.id),
            listOf(TdApi.UpdateGroupCall(call.snapshot()), TdApi.UpdateChatVideoChat(chatId, chat.videoChat)),
        )
    }

    private fun join(
        world: DemoWorld,
        function: TdApi.JoinVideoChat,
    ): DemoAnswer {
        val voiceChat =
            voiceChats[function.groupCallId]?.takeIf { it.call.isActive } ?: return badRequest("GROUPCALL_INVALID")
        val me =
            participant(DemoData.ME, order = 0).apply {
                isCurrentUser = true
                isMutedForAllUsers = function.joinParameters?.isMuted ?: true
                canUnmuteSelf = true
                canBeMutedForAllUsers = false
            }
        voiceChat.participants[DemoData.ME] = me
        voiceChat.call.isJoined = true
        voiceChat.call.participantCount = voiceChat.participants.size
        voiceChat.call.isMyVideoEnabled = function.joinParameters?.isMyVideoEnabled ?: false
        val updates =
            listOf<TdApi.Update>(TdApi.UpdateGroupCall(voiceChat.call.snapshot())) + participantUpdates(voiceChat)
        world.chat(voiceChat.chatId)?.let { chat ->
            chat.videoChat = TdApi.VideoChat(voiceChat.call.id, true, TdApi.MessageSenderUser(DemoData.ME))
        }
        return DemoAnswer(ok(TdApi.Text(EMPTY_JSON)).result, updates, later = speaking(voiceChat))
    }

    /** The others speak in turn once the user is in: Telegram tells who speaks; the demo makes it up. */
    private fun speaking(voiceChat: VoiceChat): List<DemoLater> =
        SPEAKING.map { (delay, userId, isSpeaking) ->
            DemoLater(delay) {
                val participant = voiceChat.participants[userId]
                if (!voiceChat.call.isJoined || participant == null || participant.isMutedForAllUsers) {
                    emptyList()
                } else {
                    participant.isSpeaking = isSpeaking
                    listOf(TdApi.UpdateGroupCallParticipant(voiceChat.call.id, participant.snapshot()))
                }
            }
        }

    private fun changeParticipant(
        groupCallId: Int,
        sender: TdApi.MessageSender?,
        change: TdApi.GroupCallParticipant.() -> Unit,
    ): DemoAnswer {
        val voiceChat = voiceChats[groupCallId] ?: return notFound()
        val userId = (sender as? TdApi.MessageSenderUser)?.userId ?: return badRequest("PARTICIPANT_ID_INVALID")
        val participant = voiceChat.participants[userId] ?: return badRequest("PARTICIPANT_ID_INVALID")
        participant.change()
        return DemoAnswer(
            ok(TdApi.Ok()).result,
            listOf(TdApi.UpdateGroupCallParticipant(groupCallId, participant.snapshot())),
        )
    }

    private fun changeCall(
        groupCallId: Int,
        change: TdApi.GroupCall.() -> Unit,
    ): DemoAnswer {
        val voiceChat = voiceChats[groupCallId] ?: return notFound()
        voiceChat.call.change()
        return DemoAnswer(ok(TdApi.Ok()).result, listOf(TdApi.UpdateGroupCall(voiceChat.call.snapshot())))
    }

    private fun leave(groupCallId: Int): DemoAnswer {
        val voiceChat = voiceChats[groupCallId] ?: return notFound()
        val me = voiceChat.participants.remove(DemoData.ME)
        voiceChat.call.isJoined = false
        voiceChat.call.participantCount = voiceChat.participants.size
        voiceChat.participants.values.forEach { it.isSpeaking = false }
        val gone =
            me?.let {
                it.order = ""
                TdApi.UpdateGroupCallParticipant(groupCallId, it.snapshot())
            }
        return DemoAnswer(ok(TdApi.Ok()).result, listOfNotNull(gone, TdApi.UpdateGroupCall(voiceChat.call.snapshot())))
    }

    /** The user ends the voice chat for everyone: kept ended, as the journal replays it. */
    private fun end(
        world: DemoWorld,
        groupCallId: Int,
    ): DemoAnswer {
        val voiceChat = voiceChats[groupCallId] ?: return notFound()
        if (world.groups.statusOf(voiceChat.chatId) !is TdApi.ChatMemberStatusCreator) {
            return badRequest("CHAT_ADMIN_REQUIRED")
        }
        voiceChat.call.isActive = false
        voiceChat.call.isJoined = false
        voiceChat.participants.clear()
        val noVoiceChat = TdApi.VideoChat(0, false, null)
        world.chat(voiceChat.chatId)?.videoChat = noVoiceChat
        return okChanged(
            listOf(
                TdApi.UpdateGroupCall(voiceChat.call.snapshot()),
                TdApi.UpdateChatVideoChat(voiceChat.chatId, noVoiceChat),
            ),
        )
    }

    private fun participantUpdates(voiceChat: VoiceChat): List<TdApi.Update> =
        voiceChat.participants.values.map { TdApi.UpdateGroupCallParticipant(voiceChat.call.id, it.snapshot()) }

    private fun groupCall(
        id: Int,
        title: String,
    ) = TdApi.GroupCall().apply {
        this.id = id
        uniqueId = id.toLong()
        this.title = title
        inviteLink = ""
        isActive = true
        isVideoChat = true
        canBeManaged = true
        loadedAllParticipants = true
        recentSpeakers = emptyArray()
        canEnableVideo = true
        canToggleMuteNewParticipants = true
    }

    private fun participant(
        userId: Long,
        order: Int,
    ) = TdApi.GroupCallParticipant().apply {
        participantId = TdApi.MessageSenderUser(userId)
        audioSourceId = userId.toInt()
        bio = ""
        canBeMutedForAllUsers = true
        canBeUnmutedForAllUsers = true
        volumeLevel = FULL_VOLUME
        this.order = (ORDER_BASE - order).toString()
    }

    private companion object {
        const val EMPTY_JSON = "{}"
        const val RING_AFTER = 1_000L
        const val ANSWER_AFTER = 4_000L
        const val INCOMING_AFTER = 2_000L
        const val KEYS_AFTER = 800L
        const val KEY_SEED = 1_000_003L
        const val KEY_SIZE = 256
        const val EMOJI_COUNT = 4
        const val FULL_VOLUME = 10_000
        const val ORDER_BASE = 1_000

        /** Emojis of the kind Telegram shows for a call's key. */
        val EMOJIS = listOf("🐶", "🍎", "🚲", "🌻", "🎈", "🐢", "🍋", "⭐", "🎸", "🐳", "🌈", "🍉")

        /** Who speaks when, after the user joins: Ada, then Chloe. */
        val SPEAKING =
            listOf(
                Triple(1_500L, DemoData.ADA, true),
                Triple(5_000L, DemoData.ADA, false),
                Triple(6_000L, DemoData.CHLOE, true),
                Triple(9_000L, DemoData.CHLOE, false),
            )
    }
}

/** A copy of [this] as it is now: the demo changes its objects in place, and the app reads updates later. */
private fun <T : TdApi.Object> T.snapshot(): T = DemoCodec.decode(DemoCodec.encode(this))
