package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipantVideo
import dev.kyralabs.pingogram.core.domain.model.GroupCallVideoSourceGroup
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The others' videos in a voice chat: which are received, pinning, and where their frames go (ADR 0024). */
class GroupCallManagerParticipantVideoTest {
    private val repository = FakeGroupCallRepository()
    private val engine = FakeCallEngine()

    private fun TestScope.manager() =
        GroupCallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            FakeCallClock(),
            { "Book club" },
            CallLine(),
            { FakeNetworkRepository() },
            backgroundScope,
        )

    private fun TestScope.joined(manager: GroupCallManager): FakeGroupCallSession {
        launchNow { manager.join(CHAT_ID) }
        runCurrent()
        return engine.groupSessions.single().also {
            it.state.value = CallMediaState.Connected
            runCurrent()
        }
    }

    @Test
    fun `receives the pinned video first, then those speaking, at most four, never the user's own`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(other(SELF_ID, order = "9", isCurrentUser = true))
            (1L..6L).forEach { repository.participant(other(it, order = "${7 - it}")) }
            repository.participant(other(7, order = "0", video = false))
            repository.participant(other(5, order = "2", isSpeaking = true))
            runCurrent()

            assertEquals(listOf("v5", "v1", "v2", "v3"), session.received.map { it.endpointId })
            assertEquals(5L, session.received.first().participantId)

            manager.setPinned(MessageSender.User(6))
            runCurrent()
            assertEquals(listOf("v6", "v5", "v1", "v2"), session.received.map { it.endpointId })
            val video = manager.call.value!!.video
            assertEquals(MessageSender.User(6), video.pinned)
            assertEquals(listOf("v6", "v5", "v1", "v2"), video.remote.map { it.endpointId })

            manager.setPinned(null)
            runCurrent()
            assertEquals(listOf("v5", "v1", "v2", "v3"), session.received.map { it.endpointId })
        }

    @Test
    fun `a video that stops is no longer received`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(other(1, order = "1"))
            runCurrent()
            assertEquals(listOf("v1"), session.received.map { it.endpointId })

            repository.participant(other(1, order = "1", video = false))
            runCurrent()
            assertTrue(session.received.isEmpty())
            assertTrue(
                manager.call.value!!
                    .video.remote
                    .isEmpty(),
            )
        }

    @Test
    fun `each tile's sink goes to the engine and comes back when the tile goes`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            val sink = VideoFrameSink { _, _, _, _ -> }

            manager.setParticipantVideoSink("v1", sink)
            runCurrent()
            assertEquals(mapOf("v1" to sink), session.incomingSinks)

            manager.setParticipantVideoSink("v1", null)
            runCurrent()
            assertTrue(session.incomingSinks.isEmpty())
        }

    private fun other(
        userId: Long,
        order: String,
        isSpeaking: Boolean = false,
        isCurrentUser: Boolean = false,
        video: Boolean = true,
    ) = GroupCallParticipant(
        participant = MessageSender.User(userId),
        audioSourceId = userId.toInt(),
        bio = "",
        isCurrentUser = isCurrentUser,
        isSpeaking = isSpeaking,
        isHandRaised = false,
        isMutedForAllUsers = false,
        isMutedForCurrentUser = false,
        canUnmuteSelf = false,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = order,
        video =
            if (video) {
                GroupCallParticipantVideo(
                    endpointId = "v$userId",
                    sourceGroups = persistentListOf(GroupCallVideoSourceGroup("SIM", persistentListOf(userId.toInt()))),
                    isPaused = false,
                )
            } else {
                null
            },
    )
}
