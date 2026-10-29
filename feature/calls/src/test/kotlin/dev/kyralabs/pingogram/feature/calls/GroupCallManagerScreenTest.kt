package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipantVideo
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Sharing the screen in a voice chat, and the others' shared screens (`docs/adr/0026-screen-sharing.md`). */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupCallManagerScreenTest {
    private val repository = FakeGroupCallRepository()
    private val conferences = FakeConferenceCallRepository()
    private val engine = FakeCallEngine()
    private val foreground = FakeCallForeground()

    private fun TestScope.manager() =
        GroupCallManager(
            repository,
            conferences,
            engine,
            FakeCallSystem(),
            foreground,
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
    fun `shares the screen as the presentation once the service runs as mediaProjection, and stops`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            assertTrue(
                manager.call.value!!
                    .video.canShareScreen,
            )

            manager.startScreenSharing()
            runCurrent()
            assertTrue(foreground.events.contains("screen"))
            assertEquals(listOf("open", "connect"), session.presentationEvents)
            assertEquals(listOf(FakeGroupCallSession.PRESENTATION_SOURCE), repository.screenSharing)
            assertTrue(
                manager.call.value!!
                    .video.isSharingScreen,
            )

            manager.stopScreenSharing()
            runCurrent()
            assertEquals(listOf("open", "connect", "stop"), session.presentationEvents)
            assertEquals(listOf(FakeGroupCallSession.PRESENTATION_SOURCE, -1), repository.screenSharing)
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
            assertEquals("no screen", foreground.events.last())
        }

    @Test
    fun `nothing is shared where Telegram refuses, and the capture ending by itself stops sharing`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.screenSharingResult = Outcome.Failure(DomainError.Network)

            manager.startScreenSharing()
            runCurrent()
            assertEquals(listOf("open", "stop"), session.presentationEvents)
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
            assertEquals("no screen", foreground.events.last())

            repository.screenSharingResult = Outcome.Success("{}")
            manager.startScreenSharing()
            runCurrent()
            session.endScreen.send(Unit)
            runCurrent()
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
            assertEquals(-1, repository.screenSharing.last())
        }

    @Test
    fun `receives the others' shared screens before their cameras, and names them as screens`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(other(1, order = "2", camera = true))
            repository.participant(other(2, order = "1", camera = true, screen = true))
            runCurrent()

            assertEquals(listOf("s2", "c1", "c2"), session.received.map { it.endpointId })
            val remote =
                manager.call.value!!
                    .video.remote
            assertEquals(listOf(true, false, false), remote.map { it.isScreen })
        }

    private fun other(
        userId: Long,
        order: String,
        camera: Boolean = false,
        screen: Boolean = false,
    ) = GroupCallParticipant(
        participant = MessageSender.User(userId),
        audioSourceId = userId.toInt(),
        bio = "",
        isCurrentUser = false,
        isSpeaking = false,
        isHandRaised = false,
        isMutedForAllUsers = false,
        isMutedForCurrentUser = false,
        canUnmuteSelf = false,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = order,
        video = if (camera) video("c$userId") else null,
        screen = if (screen) video("s$userId") else null,
    )

    private fun video(endpoint: String) = GroupCallParticipantVideo(endpoint, persistentListOf(), isPaused = false)
}
