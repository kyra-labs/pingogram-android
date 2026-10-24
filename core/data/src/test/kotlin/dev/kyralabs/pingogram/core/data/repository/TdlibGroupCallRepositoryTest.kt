package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipantVideo
import dev.kyralabs.pingogram.core.domain.model.GroupCallSpeaker
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoSourceGroup
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Voice chats: chats' voice chats, group calls and their participants from TDLib's updates, and the actions. */
class TdlibGroupCallRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibGroupCallRepository(client, backgroundScope)

    @Test
    fun `a chat's voice chat comes with the chat and follows its updates`() =
        runTest {
            val repository = repository()

            client.emit(
                TdApiFixtures.updateNewChat(
                    TdApiFixtures.chat(id = 7).apply {
                        videoChat = TdApi.VideoChat(5, false, TdApi.MessageSenderUser(42))
                    },
                ),
            )
            assertEquals(ChatVideoChat(5, false, MessageSender.User(42)), repository.videoChat(7).first())

            client.emit(TdApi.UpdateChatVideoChat(7, TdApi.VideoChat(5, true, null)))
            assertEquals(ChatVideoChat(5, true, null), repository.videoChat(7).first())

            client.emit(TdApi.UpdateChatVideoChat(7, TdApi.VideoChat(0, false, null)))
            assertNull(repository.videoChat(7).first())
        }

    @Test
    fun `a group call is fetched once when TDLib has not announced it, then follows its updates`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetGroupCall::class, TdResult.Success(groupCall(participantCount = 3)))

            assertEquals(
                3,
                repository
                    .groupCall(5)
                    .filterNotNull()
                    .first()
                    .participantCount,
            )
            client.emit(TdApi.UpdateGroupCall(groupCall(participantCount = 4, speakers = arrayOf(speaker(42, true)))))

            val call = repository.groupCall(5).filterNotNull().first()
            assertEquals(4, call.participantCount)
            assertEquals(persistentListOf(GroupCallSpeaker(MessageSender.User(42), true)), call.recentSpeakers)
            assertEquals(1, client.sentOf<TdApi.GetGroupCall>().size)
        }

    @Test
    fun `a group call that cannot be fetched stays unknown`() =
        runTest {
            assertNull(repository().groupCall(5).firstOrNull())
        }

    @Test
    fun `participants are listed in TDLib's order, and those who left are dropped`() =
        runTest {
            val repository = repository()

            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(1, order = "100")))
            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(2, order = "300", isSpeaking = true)))
            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(3, order = "200")))
            assertEquals(listOf(2L, 3L, 1L), repository.participants(5).first().map { it.userId() })
            assertTrue(
                repository
                    .participants(5)
                    .first()
                    .first()
                    .isSpeaking,
            )

            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(3, order = "")))
            assertEquals(listOf(2L, 1L), repository.participants(5).first().map { it.userId() })
        }

    @Test
    fun `a participant's camera comes with its endpoint and source groups, and without an endpoint as none`() =
        runTest {
            val repository = repository()
            val camera =
                TdApi.GroupCallParticipantVideoInfo(
                    arrayOf(TdApi.GroupCallVideoSourceGroup("SIM", intArrayOf(11, 12))),
                    "endpoint-1",
                    true,
                )
            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(1, order = "2").also { it.videoInfo = camera }))
            val noEndpoint = TdApi.GroupCallParticipantVideoInfo(arrayOf(), "", false)
            client.emit(
                TdApi.UpdateGroupCallParticipant(5, participant(2, order = "1").also { it.videoInfo = noEndpoint }),
            )

            client.emit(
                TdApi.UpdateGroupCallParticipant(
                    5,
                    participant(3, order = "0").also { it.screenSharingVideoInfo = camera },
                ),
            )

            val (first, second, third) = repository.participants(5).first()
            assertEquals("endpoint-1", third.screen?.endpointId)
            assertNull(third.video)
            assertEquals(
                GroupCallParticipantVideo(
                    "endpoint-1",
                    persistentListOf(GroupCallVideoSourceGroup("SIM", persistentListOf(11, 12))),
                    isPaused = true,
                ),
                first.video,
            )
            assertNull(second.video)
        }

    @Test
    fun `sharing the screen sends the presentation's payload and returns its parameters, then ends it`() =
        runTest {
            val repository = repository()
            client.on(TdApi.StartGroupCallScreenSharing::class, TdResult.Success(TdApi.Text("{\"presentation\":1}")))
            client.on(TdApi.EndGroupCallScreenSharing::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success("{\"presentation\":1}"), repository.startScreenSharing(5, 77, "{}"))
            val sent = client.sent.filterIsInstance<TdApi.StartGroupCallScreenSharing>().single()
            assertEquals(77, sent.audioSourceId)
            assertEquals("{}", sent.payload)

            assertEquals(Outcome.Success(Unit), repository.endScreenSharing(5))
            assertEquals(
                5,
                client.sent
                    .filterIsInstance<TdApi.EndGroupCallScreenSharing>()
                    .single()
                    .groupCallId,
            )
        }

    @Test
    fun `leaving or the call ending forgets its participants`() =
        runTest {
            val repository = repository()
            client.on(TdApi.LeaveGroupCall::class, TdResult.Success(TdApi.Ok()))
            client.emit(TdApi.UpdateGroupCallParticipant(5, participant(1, order = "100")))
            client.emit(TdApi.UpdateGroupCallParticipant(6, participant(1, order = "100")))

            assertEquals(Outcome.Success(Unit), repository.leave(5))
            assertTrue(repository.participants(5).first().isEmpty())

            client.emit(TdApi.UpdateGroupCall(groupCall(id = 6, isActive = false)))
            assertTrue(repository.participants(6).first().isEmpty())
        }

    @Test
    fun `joining sends the engine's payload and returns the call's parameters`() =
        runTest {
            val repository = repository()
            client.on(TdApi.JoinVideoChat::class, TdResult.Success(TdApi.Text("{\"transport\":{}}")))

            val answer = repository.join(5, MessageSender.Chat(-100), 1234, "{\"ssrc\":1234}", isMuted = true)

            assertEquals(Outcome.Success("{\"transport\":{}}"), answer)
            val sent = client.sentOf<TdApi.JoinVideoChat>().single()
            assertEquals(5, sent.groupCallId)
            assertEquals(-100L, (sent.participantId as TdApi.MessageSenderChat).chatId)
            assertEquals(1234, sent.joinParameters.audioSourceId)
            assertEquals("{\"ssrc\":1234}", sent.joinParameters.payload)
            assertTrue(sent.joinParameters.isMuted)
            assertEquals(false, sent.joinParameters.isMyVideoEnabled)
        }

    @Test
    fun `starting a voice chat creates one now and returns its call`() =
        runTest {
            client.on(TdApi.CreateVideoChat::class, TdResult.Success(TdApi.GroupCallId(9)))

            assertEquals(Outcome.Success(9), repository().startVideoChat(7))

            val sent = client.sentOf<TdApi.CreateVideoChat>().single()
            assertEquals(7L, sent.chatId)
            assertEquals(0, sent.startDate)
            assertEquals(false, sent.isRtmpStream)
        }

    @Test
    fun `muting, hands and ending go to TDLib`() =
        runTest {
            val repository = repository()
            client.on(TdApi.ToggleGroupCallParticipantIsMuted::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleGroupCallParticipantIsHandRaised::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.EndGroupCall::class, TdResult.Success(TdApi.Ok()))

            repository.setMuted(5, MessageSender.User(42), isMuted = false)
            repository.setHandRaised(5, MessageSender.User(42), isHandRaised = true)
            repository.end(5)

            val mute = client.sentOf<TdApi.ToggleGroupCallParticipantIsMuted>().single()
            assertEquals(false, mute.isMuted)
            assertEquals(42L, (mute.participantId as TdApi.MessageSenderUser).userId)
            assertTrue(client.sentOf<TdApi.ToggleGroupCallParticipantIsHandRaised>().single().isHandRaised)
            assertEquals(5, client.sentOf<TdApi.EndGroupCall>().single().groupCallId)
        }

    @Test
    fun `a live stream's channels and segments come from TDLib`() =
        runTest {
            val repository = repository()
            client.on(
                TdApi.GetGroupCallStreams::class,
                TdResult.Success(TdApi.GroupCallStreams(arrayOf(TdApi.GroupCallStream(1, 0, 5000)))),
            )
            client.on(TdApi.GetGroupCallStreamSegment::class, TdResult.Success(TdApi.Data(byteArrayOf(1, 2))))

            assertEquals(Outcome.Success(persistentListOf(GroupCallStream(1, 0, 5000))), repository.streams(5))
            val segment = repository.streamSegment(5, 5000, 0, 2, GroupCallVideoQuality.Medium)

            assertArrayEquals(byteArrayOf(1, 2), (segment as StreamSegment.Data).bytes)
            val sent = client.sentOf<TdApi.GetGroupCallStreamSegment>().single()
            assertEquals(5000L, sent.timeOffset)
            assertEquals(2, sent.channelId)
            assertTrue(sent.videoQuality is TdApi.GroupCallVideoQualityMedium)
        }

    @Test
    fun `a segment not recorded yet is not ready, any other failure unavailable`() =
        runTest {
            val repository = repository()

            client.on(TdApi.GetGroupCallStreamSegment::class, TdResult.Failure(TdError(400, "TIME_TOO_BIG")))
            assertEquals(StreamSegment.NotReady, repository.streamSegment(5, 9000, 0, 0, null))

            client.on(TdApi.GetGroupCallStreamSegment::class, TdResult.Failure(TdError(400, "TIME_TOO_SMALL")))
            assertEquals(StreamSegment.Unavailable, repository.streamSegment(5, 1000, 0, 0, null))
            assertNull(client.sentOf<TdApi.GetGroupCallStreamSegment>().last().videoQuality)
        }

    private fun dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant.userId() =
        (participant as MessageSender.User).userId

    private fun speaker(
        userId: Long,
        isSpeaking: Boolean,
    ) = TdApi.GroupCallRecentSpeaker(TdApi.MessageSenderUser(userId), isSpeaking)

    private fun groupCall(
        id: Int = 5,
        isActive: Boolean = true,
        participantCount: Int = 0,
        speakers: Array<TdApi.GroupCallRecentSpeaker> = emptyArray(),
    ) = TdApi.GroupCall().also {
        it.id = id
        it.title = ""
        it.isActive = isActive
        it.isVideoChat = true
        it.participantCount = participantCount
        it.recentSpeakers = speakers
    }

    private fun participant(
        userId: Long,
        order: String,
        isSpeaking: Boolean = false,
    ) = TdApi.GroupCallParticipant().also {
        it.participantId = TdApi.MessageSenderUser(userId)
        it.audioSourceId = userId.toInt()
        it.bio = ""
        it.isSpeaking = isSpeaking
        it.order = order
    }
}
