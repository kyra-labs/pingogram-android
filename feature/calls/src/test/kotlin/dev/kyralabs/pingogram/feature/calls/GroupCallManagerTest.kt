package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallStream
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoQuality
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.StreamSegment
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.GroupCallMode
import dev.kyralabs.pingogram.feature.calls.engine.SegmentResult
import dev.kyralabs.pingogram.feature.calls.engine.StreamQuality
import dev.kyralabs.pingogram.feature.calls.engine.StreamRequest
import dev.kyralabs.pingogram.feature.calls.system.SystemEndCause
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The voice chat's state machine: joining, the microphone, hands, leaving, the call ending, and live streams. */
class GroupCallManagerTest {
    private val repository = FakeGroupCallRepository()
    private val engine = FakeCallEngine()
    private val system = FakeCallSystem()
    private val foreground = FakeCallForeground()
    private val clock = FakeCallClock()
    private val line = CallLine()

    private fun TestScope.manager() =
        GroupCallManager(repository, engine, system, foreground, clock, { "Book club" }, line, backgroundScope)

    private fun TestScope.joined(manager: GroupCallManager): FakeGroupCallSession {
        launchNow { manager.join(CHAT_ID) }
        runCurrent()
        return engine.groupSessions.single()
    }

    @Test
    fun `joining makes the engine's payload, joins muted through TDLib and connects with its answer`() =
        runTest {
            val manager = manager()

            val session = joined(manager)

            val join = repository.joins.single()
            assertEquals(CALL_ID, join.id)
            assertEquals(MessageSender.User(SELF_ID), join.participant)
            assertEquals(FakeGroupCallSession.AUDIO_SOURCE, join.audioSourceId)
            assertEquals(session.joinPayload, join.payload)
            assertTrue(join.isMuted)
            assertTrue(session.muted)
            assertEquals(FakeGroupCallRepository.PARAMS, session.params)
            assertEquals(listOf(CALL_ID), repository.loaded)
            assertEquals(GroupCallPhase.Joining, manager.call.value?.phase)
            assertEquals(
                "pingogram:chat/$CHAT_ID",
                system.calls
                    .single()
                    .request.address,
            )
            assertEquals(
                "Book club",
                system.calls
                    .single()
                    .request.displayName,
            )
            assertEquals(listOf("start"), foreground.events)
            assertTrue(line.isBusy)

            session.state.value = CallMediaState.Connected
            runCurrent()

            assertEquals(GroupCallPhase.Connected, manager.call.value?.phase)
            assertEquals(clock.now, manager.call.value?.connectedAtMillis)
            assertTrue(system.calls.single().active)
        }

    @Test
    fun `nothing joins while the user is in another call, or where the chat has no voice chat`() =
        runTest {
            val manager = manager()
            line.take(Any())
            assertEquals(JoinResult.Busy, manager.join(CHAT_ID))

            val free = CallLine()
            val other =
                GroupCallManager(repository, engine, system, foreground, clock, { "" }, free, backgroundScope)
            repository.videoChats.value = emptyMap()
            assertEquals(JoinResult.NoVoiceChat, other.join(CHAT_ID))
            assertTrue(engine.groupSessions.isEmpty())
            assertNull(other.call.value)
        }

    @Test
    fun `unmuting tells TDLib and the engine, and the service may use the microphone from then on`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(self())
            runCurrent()

            manager.setMuted(false)
            runCurrent()

            assertFalse(session.muted)
            assertEquals(listOf(MuteRequest(MessageSender.User(SELF_ID), false)), repository.mutes)
            assertEquals(listOf("start", "start+mic"), foreground.events)
            assertEquals(false, manager.call.value?.isMuted)
        }

    @Test
    fun `muted by an administrator, the user cannot unmute and raises a hand instead`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(self())
            runCurrent()
            manager.setMuted(false)
            runCurrent()

            repository.participant(self(isMutedForAllUsers = true, canUnmuteSelf = false))
            runCurrent()

            assertTrue(session.muted)
            assertEquals(false, manager.call.value?.canUnmute)
            manager.setMuted(false)
            runCurrent()
            assertTrue(session.muted)

            manager.setHandRaised(true)
            assertEquals(listOf(MessageSender.User(SELF_ID) to true), repository.hands)
        }

    @Test
    fun `leaving tells TDLib, closes the media and gives the line back`() =
        runTest {
            val manager = manager()
            val session = joined(manager)

            manager.leave()
            runCurrent()

            assertEquals(listOf(CALL_ID), repository.left)
            assertTrue(session.closed)
            assertFalse(line.isBusy)
            assertEquals(GroupCallPhase.Ended(GroupCallEndReason.Left), manager.call.value?.phase)
            assertEquals(SystemEndCause.Local, system.calls.single().ended)
            assertEquals("stop", foreground.events.last())

            advanceTimeBy(3_000)
            assertNull(manager.call.value)
        }

    @Test
    fun `a voice chat that ends ends for the user`() =
        runTest {
            val manager = manager()
            val session = joined(manager)

            repository.calls.value = mapOf(CALL_ID to groupCall(isActive = false))
            runCurrent()

            assertEquals(GroupCallPhase.Ended(GroupCallEndReason.Ended), manager.call.value?.phase)
            assertTrue(session.closed)
        }

    @Test
    fun `an administrator ends it for everyone`() =
        runTest {
            val manager = manager()
            joined(manager)
            repository.calls.value = mapOf(CALL_ID to groupCall(canBeManaged = true))
            runCurrent()
            assertEquals(true, manager.call.value?.canEnd)

            assertEquals(Outcome.Success(Unit), manager.endForEveryone())

            assertEquals(listOf(CALL_ID), repository.ended)
            assertEquals(GroupCallPhase.Ended(GroupCallEndReason.Ended), manager.call.value?.phase)
        }

    @Test
    fun `a join Telegram refuses ends with the reason`() =
        runTest {
            repository.joinResult = Outcome.Failure(DomainError.AdminRightsRequired)
            val manager = manager()

            val session = joined(manager)

            assertEquals(
                GroupCallPhase.Ended(GroupCallEndReason.Failed(DomainError.AdminRightsRequired)),
                manager.call.value?.phase,
            )
            assertTrue(session.closed)
            assertFalse(line.isBusy)
        }

    @Test
    fun `a lost connection that cannot be found again leaves`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            session.state.value = CallMediaState.Connected
            runCurrent()

            session.state.value = CallMediaState.Connecting
            runCurrent()
            assertEquals(GroupCallPhase.Reconnecting, manager.call.value?.phase)

            session.state.value = CallMediaState.Failed
            runCurrent()
            assertEquals(GroupCallPhase.Ended(GroupCallEndReason.Disconnected), manager.call.value?.phase)
            assertEquals(listOf(CALL_ID), repository.left)
        }

    @Test
    fun `a live stream gets its time and segments from TDLib`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            session.mode.value = GroupCallMode.Stream
            runCurrent()
            assertEquals(true, manager.call.value?.isStream)
            assertEquals(false, manager.call.value?.canUnmute)

            session.ask(StreamRequest.Time)
            val video = StreamRequest.Segment(9000, 1, 9000, 2, StreamQuality.Medium)
            session.ask(video)
            runCurrent()

            assertEquals(listOf(9000L), session.times)
            assertEquals(video, session.segments.single().first)
            assertArrayEquals(byteArrayOf(7), (session.segments.single().second as SegmentResult.Data).bytes)
            assertEquals(SegmentRequest(9000, 0, 2, GroupCallVideoQuality.Medium), repository.segments.single())

            repository.segment = StreamSegment.NotReady
            val audio = StreamRequest.Segment(10_000, 0, 10_000, 0, null)
            session.ask(audio)
            runCurrent()
            assertEquals(SegmentResult.NotReady, session.segments.last().second)
            assertNull(repository.segments.last().quality)
        }

    @Test
    fun `starting a voice chat creates one and joins it`() =
        runTest {
            repository.videoChats.value = emptyMap()
            val manager = manager()

            launchNow { manager.startVoiceChat(CHAT_ID) }
            runCurrent()

            assertEquals(listOf(CHAT_ID), repository.started)
            assertEquals(CALL_ID, repository.joins.single().id)
        }

    private fun self(
        isMutedForAllUsers: Boolean = false,
        canUnmuteSelf: Boolean = false,
    ) = GroupCallParticipant(
        participant = MessageSender.User(SELF_ID),
        audioSourceId = FakeGroupCallSession.AUDIO_SOURCE,
        bio = "",
        isCurrentUser = true,
        isSpeaking = false,
        isHandRaised = false,
        isMutedForAllUsers = isMutedForAllUsers,
        isMutedForCurrentUser = false,
        canUnmuteSelf = canUnmuteSelf,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = "1",
    )
}
