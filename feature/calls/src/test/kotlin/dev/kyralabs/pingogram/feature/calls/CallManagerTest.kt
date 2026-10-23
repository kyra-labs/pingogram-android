package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.feature.calls.engine.CallCamera
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import dev.kyralabs.pingogram.feature.calls.engine.VideoQuality
import dev.kyralabs.pingogram.feature.calls.system.SystemEndCause
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** The call state machine: TDLib's signaling, the engine, Telecom, the service and the tones, one call at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTest {
    private val repository = FakeCallRepository()
    private val engine = FakeCallEngine()
    private val system = FakeCallSystem()
    private val foreground = FakeCallForeground()
    private val tones = FakeCallTones()
    private val clock = FakeCallClock()
    private val network = FakeNetworkRepository()

    private fun TestScope.manager() =
        CallManager(repository, engine, system, foreground, tones, clock, { "Ada" }, { network }, backgroundScope).also {
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

    private fun call(
        state: CallState,
        id: Int = FakeCallRepository.STARTED_CALL_ID,
        isOutgoing: Boolean = true,
    ) = Call(id, userId = 42, isOutgoing = isOutgoing, isVideo = false, state = state)

    private fun TestScope.callOut(manager: CallManager) {
        backgroundScope.launchNow { manager.startCall(42) }
        repository.update(call(CallState.Pending(isCreated = true, isReceived = false)))
        runCurrent()
    }

    @Test
    fun `calls with the engine's protocol, then rings`() =
        runTest {
            val manager = manager()

            callOut(manager)

            assertEquals(listOf(42L to ENGINE_PROTOCOL), repository.started)
            val active = manager.call.value!!
            assertEquals(FakeCallRepository.STARTED_CALL_ID, active.callId)
            assertEquals(CallPhase.Ringing(reachedDevice = false), active.phase)
            assertEquals("start+mic", foreground.events.first())
            assertEquals(SystemCallKind("Ada", 42, isOutgoing = true), system.calls.single().kind())
        }

    @Test
    fun `plays the ringback tone once the other phone rings`() =
        runTest {
            val manager = manager()
            callOut(manager)

            repository.update(call(CallState.Pending(isCreated = true, isReceived = true)))
            runCurrent()

            assertEquals(CallPhase.Ringing(reachedDevice = true), manager.call.value?.phase)
            assertEquals(listOf("ringback"), tones.played)
        }

    @Test
    fun `connects the engine with what TDLib agreed on, and counts from the connection`() =
        runTest {
            val manager = manager()
            callOut(manager)

            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()
            assertEquals(CallPhase.Connecting, manager.call.value?.phase)
            assertEquals(connection, session.connection)
            assertTrue(session.isOutgoing)

            session.state.value = CallMediaState.Connected
            runCurrent()

            val active = manager.call.value!!
            assertEquals(CallPhase.Connected, active.phase)
            assertEquals(clock.now, active.connectedAtMillis)
            assertEquals(persistentListOf("😀", "🐶", "🍎", "🚗"), active.emojis)
            assertTrue(system.calls.single().active)
        }

    @Test
    fun `hangs up with how long it lasted, and ends once TDLib says so`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            engine.sessions
                .single()
                .state.value = CallMediaState.Connected
            runCurrent()
            clock.now += 61_000

            manager.hangUp()
            runCurrent()
            assertEquals(listOf(FakeCallRepository.Discard(7, false, 61.seconds)), repository.discarded)

            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = false)))
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.HungUp), manager.call.value?.phase)
            assertTrue(engine.sessions.single().closed)
            assertEquals(SystemEndCause.Local, system.calls.single().ended)
            assertEquals("stop", foreground.events.last())

            advanceTimeBy(3.seconds)
            assertNull(manager.call.value)
        }

    @Test
    fun `an incoming call rings, and answering accepts it with the engine's protocol`() =
        runTest {
            val manager = manager()

            repository.update(call(CallState.Pending(isCreated = true, isReceived = true), id = 3, isOutgoing = false))
            runCurrent()
            val ringing = manager.call.value!!
            assertEquals(CallPhase.Ringing(reachedDevice = true), ringing.phase)
            assertFalse(ringing.isOutgoing)
            assertEquals(listOf("start"), foreground.events)

            manager.answer()
            runCurrent()

            assertEquals(listOf(3 to ENGINE_PROTOCOL), repository.accepted)
            assertEquals(CallPhase.Connecting, manager.call.value?.phase)
            assertTrue(system.calls.single().answered)
            assertEquals("start+mic", foreground.events.last())
        }

    @Test
    fun `an incoming video call rings as a voice call that says so`() =
        runTest {
            val manager = manager()

            repository.update(
                Call(
                    3,
                    42,
                    isOutgoing = false,
                    isVideo = true,
                    state = CallState.Pending(isCreated = true, isReceived = true),
                ),
            )
            runCurrent()

            assertTrue(manager.call.value!!.videoOffered)
            manager.answer()
            runCurrent()
            assertEquals(listOf(3 to ENGINE_PROTOCOL), repository.accepted)
        }

    @Test
    fun `the other side turning video on says the call stays audio only`() =
        runTest {
            val manager = manager()
            callOut(manager)
            assertFalse(manager.call.value!!.videoOffered)

            repository.update(Call(7, 42, isOutgoing = true, isVideo = true, state = CallState.Ready(connection)))
            runCurrent()

            assertTrue(manager.call.value!!.videoOffered)
        }

    @Test
    fun `a second incoming call while in one is declined`() =
        runTest {
            val manager = manager()
            callOut(manager)

            repository.update(call(CallState.Pending(isCreated = true, isReceived = true), id = 9, isOutgoing = false))
            runCurrent()

            assertEquals(listOf(9), repository.discarded.map { it.callId })
            assertEquals(FakeCallRepository.STARTED_CALL_ID, manager.call.value?.callId)
        }

    @Test
    fun `a call the other person's privacy refuses ends with the reason`() =
        runTest {
            repository.startResult = Outcome.Failure(DomainError.CallPrivacyRestricted)
            val manager = manager()

            manager.startCall(42)
            runCurrent()

            assertEquals(
                CallPhase.Ended(CallEndReason.Failed(DomainError.CallPrivacyRestricted)),
                manager.call.value?.phase,
            )
            assertEquals("stop", foreground.events.last())
        }

    @Test
    fun `an engine that cannot load ends the call instead of calling`() =
        runTest {
            engine.failsToLoad = true
            val manager = manager()

            manager.startCall(42)
            runCurrent()

            assertTrue(repository.started.isEmpty())
            assertTrue(manager.call.value?.phase is CallPhase.Ended)
        }

    @Test
    fun `keeps the other side's data that comes before the engine, then passes everything on`() =
        runTest {
            val manager = manager()
            callOut(manager)

            repository.signaling.emit(CallSignalingData(7, byteArrayOf(1)))
            runCurrent()
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            repository.signaling.emit(CallSignalingData(7, byteArrayOf(2)))
            runCurrent()

            assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2)), engine.sessions.single().received)
        }

    @Test
    fun `sends the engine's data to the other side`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            engine.sessions.single().send(byteArrayOf(5, 6))
            runCurrent()

            assertEquals(listOf(7 to listOf<Byte>(5, 6)), repository.sent)
        }

    @Test
    fun `a lost connection reconnects, and an engine that gives up hangs up as disconnected`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()
            session.state.value = CallMediaState.Connected
            runCurrent()

            session.state.value = CallMediaState.Connecting
            runCurrent()
            assertEquals(CallPhase.Reconnecting, manager.call.value?.phase)

            session.state.value = CallMediaState.Failed
            runCurrent()
            assertEquals(listOf(true), repository.discarded.map { it.isDisconnected })
        }

    @Test
    fun `mutes the engine, also when the system's mute changes`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()

            manager.setMuted(true)
            runCurrent()
            assertTrue(session.muted && manager.call.value!!.isMuted)

            system.calls
                .single()
                .isMuted.value = true
            runCurrent()
            system.calls
                .single()
                .isMuted.value = false
            runCurrent()
            assertFalse(session.muted || manager.call.value!!.isMuted)
        }

    @Test
    fun `a declined call plays the busy tone`() =
        runTest {
            val manager = manager()
            callOut(manager)

            repository.update(call(CallState.Discarded(CallDiscardReason.Declined, needsRating = false)))
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.Declined), manager.call.value?.phase)
            assertEquals("busy", tones.played.last())
            assertEquals(SystemEndCause.Rejected, system.calls.single().ended)
        }

    @Test
    fun `an incoming call the caller gave up on is missed`() =
        runTest {
            val manager = manager()
            repository.update(call(CallState.Pending(isCreated = true, isReceived = true), id = 3, isOutgoing = false))
            runCurrent()

            repository.update(
                call(CallState.Discarded(CallDiscardReason.Missed, needsRating = false), id = 3, isOutgoing = false),
            )
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.Missed), manager.call.value?.phase)
        }

    @Test
    fun `hanging up before Telegram created the call ends it there too`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.startGate = gate
            val manager = manager()
            backgroundScope.launchNow { manager.startCall(42) }
            manager.hangUp()
            gate.complete(Unit)
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.Missed), manager.call.value?.phase)
            assertEquals(listOf(7), repository.discarded.map { it.callId })
        }

    @Test
    fun `Telecom's hold holds the engine`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            system.calls
                .single()
                .listener
                .onHold(true)
            runCurrent()

            assertTrue(engine.sessions.single().onHold)
            assertTrue(manager.call.value!!.isOnHold)
        }

    @Test
    fun `a video call is made as one and sends the front camera once connected`() =
        runTest {
            val manager = manager()

            backgroundScope.launchNow { manager.startCall(42, withVideo = true) }
            repository.update(call(CallState.Pending(isCreated = true, isReceived = false)))
            runCurrent()
            assertEquals(listOf(true), repository.startedVideo)
            assertEquals(CallVideo(CameraFacing.Front, canSwitchCamera = true), manager.call.value?.video)

            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            assertEquals(
                FRONT_CAMERA,
                engine.sessions
                    .single()
                    .cameras
                    .last(),
            )
        }

    @Test
    fun `turns the camera on and off, and switches between front and back`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()
            assertEquals(listOf<CallCamera?>(null), session.cameras)
            assertEquals(CallVideo(), manager.call.value?.video)

            manager.setCameraOn(true)
            runCurrent()
            assertEquals(FRONT_CAMERA, session.cameras.last())
            assertEquals(
                CameraFacing.Front,
                manager.call.value
                    ?.video
                    ?.camera,
            )

            manager.switchCamera()
            runCurrent()
            assertEquals(BACK_CAMERA, session.cameras.last())
            assertEquals(
                CameraFacing.Back,
                manager.call.value
                    ?.video
                    ?.camera,
            )

            manager.setCameraOn(false)
            runCurrent()
            assertNull(session.cameras.last())
            assertNull(
                manager.call.value
                    ?.video
                    ?.camera,
            )

            // On again: the camera last used.
            manager.setCameraOn(true)
            runCurrent()
            assertEquals(BACK_CAMERA, session.cameras.last())
        }

    @Test
    fun `a device with one camera sends it and cannot switch`() =
        runTest {
            engine.cameras = listOf(BACK_CAMERA)
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            manager.setCameraOn(true)
            runCurrent()
            assertEquals(CallVideo(CameraFacing.Back, canSwitchCamera = false), manager.call.value?.video)
        }

    @Test
    fun `a device without a camera keeps it off`() =
        runTest {
            engine.cameras = emptyList()
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            manager.setCameraOn(true)
            runCurrent()

            assertEquals(CallVideo(), manager.call.value?.video)
        }

    @Test
    fun `holding the call stops the camera until it is taken back`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            manager.setCameraOn(true)
            runCurrent()
            val session = engine.sessions.single()
            session.state.value = CallMediaState.Connected
            runCurrent()

            manager.setOnHold(true)
            runCurrent()
            assertNull(session.cameras.last())
            assertNull(
                manager.call.value
                    ?.video
                    ?.camera,
            )

            manager.setOnHold(false)
            runCurrent()
            assertEquals(FRONT_CAMERA, session.cameras.last())
        }

    @Test
    fun `follows the other side's video and hangs up as a video call`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()

            session.remoteVideo.value = RemoteVideo.Active
            runCurrent()
            assertEquals(
                RemoteVideo.Active,
                manager.call.value
                    ?.video
                    ?.remote,
            )
            assertTrue(manager.call.value!!.videoOffered)

            session.remoteVideo.value = RemoteVideo.Paused
            runCurrent()
            assertEquals(
                RemoteVideo.Paused,
                manager.call.value
                    ?.video
                    ?.remote,
            )

            manager.hangUp()
            runCurrent()
            assertEquals(listOf(true), repository.discardedVideo)
        }

    @Test
    fun `a voice call hangs up as one`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()

            manager.hangUp()
            runCurrent()

            assertEquals(listOf(false), repository.startedVideo)
            assertEquals(listOf(false), repository.discardedVideo)
        }

    @Test
    fun `hands the frame sinks to the engine, also those set before it connected`() =
        runTest {
            val manager = manager()
            callOut(manager)
            val local = VideoFrameSink { _, _, _, _ -> }
            val remote = VideoFrameSink { _, _, _, _ -> }

            manager.setVideoSinks(local, remote)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            val session = engine.sessions.single()
            assertEquals(local, session.localSink)
            assertEquals(remote, session.remoteSink)

            manager.setVideoSinks(null, null)
            runCurrent()
            assertNull(session.localSink)
            assertNull(session.remoteSink)
        }

    @Test
    fun `answering with video sends the front camera`() =
        runTest {
            val manager = manager()
            repository.update(call(CallState.Pending(isCreated = true, isReceived = true), id = 3, isOutgoing = false))
            runCurrent()

            manager.answer(withVideo = true)
            repository.update(call(CallState.Ready(connection), id = 3, isOutgoing = false))
            runCurrent()

            assertEquals(
                FRONT_CAMERA,
                engine.sessions
                    .single()
                    .cameras
                    .last(),
            )
        }

    @Test
    fun `sends 720p on Wi-Fi, 480p on mobile data, 360p roaming or with Data Saver`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            manager.setCameraOn(true)
            runCurrent()
            val session = engine.sessions.single()
            assertEquals(VideoQuality.High, session.qualities.last())

            network.networkStatus.value = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = false)
            runCurrent()
            assertEquals(VideoQuality.Medium, session.qualities.last())

            network.networkStatus.value = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = true)
            runCurrent()
            assertEquals(VideoQuality.Low, session.qualities.last())

            network.networkStatus.value = NetworkStatus(NetworkType.Roaming, isMetered = true, isDataSaverOn = false)
            runCurrent()
            assertEquals(VideoQuality.Low, session.qualities.last())

            // Offline while the call reconnects: the last network's quality stays.
            network.networkStatus.value = NetworkStatus.Offline
            runCurrent()
            assertEquals(VideoQuality.Low, session.qualities.last())
            assertEquals(FRONT_CAMERA, session.cameras.last())
        }

    @Test
    fun `a call whose connection was lost sends at most 480p from then on`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            manager.setCameraOn(true)
            val session = engine.sessions.single()
            session.state.value = CallMediaState.Connected
            runCurrent()

            session.state.value = CallMediaState.Connecting
            runCurrent()
            session.state.value = CallMediaState.Connected
            runCurrent()

            assertEquals(VideoQuality.Medium, session.qualities.last())
        }

    @Test
    fun `an engine that cannot list cameras leaves the camera off`() =
        runTest {
            val manager = manager()
            callOut(manager)
            repository.update(call(CallState.Ready(connection)))
            runCurrent()
            engine.failsToLoad = true

            manager.setCameraOn(true)
            runCurrent()

            assertNull(
                engine.sessions
                    .single()
                    .cameras
                    .last(),
            )
            assertNull(
                manager.call.value
                    ?.video
                    ?.camera,
            )
        }
}

private data class SystemCallKind(
    val name: String,
    val userId: Long,
    val isOutgoing: Boolean,
)

private fun FakeSystemCall.kind() = SystemCallKind(request.displayName, request.userId, request.isOutgoing)
