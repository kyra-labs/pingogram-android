package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Screen sharing in one-to-one calls (`docs/adr/0026-screen-sharing.md`). */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerScreenTest {
    private val repository = FakeCallRepository()
    private val engine = FakeCallEngine()
    private val foreground = FakeCallForeground()
    private val system = FakeCallSystem()

    private fun TestScope.manager() =
        CallManager(
            repository,
            engine,
            system,
            foreground,
            FakeCallTones(),
            FakeCallClock(),
            { _, _ -> "Ada" },
            { FakeNetworkRepository() },
            CallLine(),
            backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }

    private val connection =
        CallConnection(
            protocol = ENGINE_PROTOCOL,
            servers = persistentListOf(),
            encryptionKey = ByteArray(256) { 1 },
            emojis = persistentListOf("😀", "🐶", "🍎", "🚗"),
            allowP2p = true,
            customParameters = "",
        )

    private fun call(state: CallState) =
        Call(FakeCallRepository.STARTED_CALL_ID, userId = 42, isOutgoing = true, isVideo = false, state = state)

    private fun TestScope.connected(manager: CallManager): FakeCallSession {
        repository.startResult = Outcome.Success(FakeCallRepository.STARTED_CALL_ID)
        backgroundScope.launchNow { manager.startCall(42) }
        repository.update(call(CallState.Pending(isCreated = true, isReceived = false)))
        runCurrent()
        repository.update(call(CallState.Ready(connection)))
        runCurrent()
        return engine.sessions.last().also {
            it.state.value = CallMediaState.Connected
            runCurrent()
        }
    }

    @Test
    fun `shares the screen once the service runs as mediaProjection, in place of the camera, and stops`() =
        runTest {
            val manager = manager()
            val session = connected(manager)
            manager.setCameraOn(true)
            runCurrent()

            manager.startScreenSharing()
            runCurrent()
            assertTrue(foreground.events.contains("screen"))
            assertEquals(listOf(false, true), session.screens)
            val video = manager.call.value!!.video
            assertTrue(video.isSharingScreen)
            assertNull(video.camera)

            manager.stopScreenSharing()
            runCurrent()
            assertEquals(listOf(false, true, false), session.screens)
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
            assertEquals("no screen", foreground.events.last())
        }

    @Test
    fun `nothing is shared where Android refuses the service its type`() =
        runTest {
            foreground.allowsScreenCapture = false
            val manager = manager()
            val session = connected(manager)

            manager.startScreenSharing()
            runCurrent()
            assertEquals(listOf(false), session.screens)
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
        }

    @Test
    fun `the camera, the capture ending by itself and hold each stop or pause the screen`() =
        runTest {
            val manager = manager()
            val session = connected(manager)
            manager.startScreenSharing()
            runCurrent()

            system.calls
                .single()
                .listener
                .onHold(true)
            runCurrent()
            assertEquals(listOf(false, true, false), session.screens)
            system.calls
                .single()
                .listener
                .onHold(false)
            runCurrent()
            assertEquals(listOf(false, true, false, true), session.screens)

            session.endScreen.send(Unit)
            runCurrent()
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )

            manager.startScreenSharing()
            runCurrent()
            manager.setCameraOn(true)
            runCurrent()
            assertEquals(false, session.screens.last())
            assertFalse(
                manager.call.value!!
                    .video.isSharingScreen,
            )
        }

    @Test
    fun `the other side's shared screen shows as their video`() =
        runTest {
            val manager = manager()
            val session = connected(manager)

            session.remoteVideo.value = RemoteVideo.Active
            session.remoteIsScreen.value = true
            runCurrent()
            val video = manager.call.value!!.video
            assertEquals(RemoteVideo.Active, video.remote)
            assertTrue(video.remoteIsScreen)
        }
}
