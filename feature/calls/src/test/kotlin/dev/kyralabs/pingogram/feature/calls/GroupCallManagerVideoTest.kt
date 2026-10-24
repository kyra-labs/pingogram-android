package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import dev.kyralabs.pingogram.feature.calls.engine.VideoQuality
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The user's camera in a voice chat (`docs/adr/0024-video-in-voice-chats.md`). */
class GroupCallManagerVideoTest {
    private val repository = FakeGroupCallRepository()
    private val conferences = FakeConferenceCallRepository()
    private val engine = FakeCallEngine()
    private val system = FakeCallSystem()
    private val network = FakeNetworkRepository()

    private fun TestScope.manager() =
        GroupCallManager(
            repository,
            conferences,
            engine,
            system,
            FakeCallForeground(),
            FakeCallClock(),
            { "Book club" },
            CallLine(),
            { network },
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
    fun `sends the front camera, tells TDLib, and switches and stops it`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            assertTrue(
                manager.call.value!!
                    .video.canSend,
            )

            manager.setCameraOn(true)
            runCurrent()
            assertEquals(FRONT_CAMERA to VideoQuality.High, session.camera)
            assertEquals(
                CameraFacing.Front,
                manager.call.value
                    ?.video
                    ?.camera,
            )
            assertTrue(
                manager.call.value!!
                    .video.canSwitchCamera,
            )
            assertEquals(listOf(true), repository.videoToggles)

            manager.switchCamera()
            runCurrent()
            assertEquals(BACK_CAMERA to VideoQuality.High, session.camera)

            manager.setCameraOn(false)
            runCurrent()
            assertNull(session.camera)
            assertEquals(listOf(true, false), repository.videoToggles)
        }

    @Test
    fun `sends less on mobile data, and nothing while held`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            manager.setCameraOn(true)
            runCurrent()

            network.networkStatus.value = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = false)
            runCurrent()
            assertEquals(FRONT_CAMERA to VideoQuality.Medium, session.camera)

            system.calls
                .single()
                .listener
                .onHold(true)
            runCurrent()
            assertNull(session.camera)
            assertTrue(session.onHold)
        }

    @Test
    fun `no camera where Telegram allows no video, and it stops when Telegram takes video back`() =
        runTest {
            repository.calls.value = mapOf(CALL_ID to groupCall(canEnableVideo = false))
            val manager = manager()
            val session = joined(manager)

            manager.setCameraOn(true)
            runCurrent()
            assertNull(session.camera)
            assertFalse(
                manager.call.value!!
                    .video.canSend,
            )

            repository.calls.value = mapOf(CALL_ID to groupCall())
            runCurrent()
            manager.setCameraOn(true)
            runCurrent()
            assertEquals(FRONT_CAMERA to VideoQuality.High, session.camera)

            repository.calls.value = mapOf(CALL_ID to groupCall(canEnableVideo = false))
            runCurrent()
            assertNull(session.camera)
            assertNull(
                manager.call.value
                    ?.video
                    ?.camera,
            )
        }

    @Test
    fun `the self-view's frames go to the sink the screen sets`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            val sink = VideoFrameSink { _, _, _, _ -> }

            manager.setSelfVideoSink(sink)
            runCurrent()
            assertEquals(sink, session.selfSink)

            manager.setSelfVideoSink(null)
            runCurrent()
            assertNull(session.selfSink)
        }
}
