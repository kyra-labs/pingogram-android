package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallSignalingData
import dev.kyralabs.pingogram.core.domain.model.CallState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A one-to-one call and its engine: a session that connects after the call ended is closed, and the other side's
 * signaling data reaches the engine one packet at a time, in the order it came.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerEngineTest {
    private val repository = FakeCallRepository()
    private val engine = FakeCallEngine()

    private fun TestScope.manager() =
        CallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            FakeCallTones(),
            FakeCallClock(),
            { _, _ -> "Ada" },
            { FakeNetworkRepository() },
            CallLine(),
            { error("No conference calls in these tests") },
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

    /** A call to user 42 that TDLib has set up: the engine connects. */
    private fun TestScope.ready(manager: CallManager) {
        backgroundScope.launchNow { manager.startCall(42) }
        repository.update(call(CallState.Pending(isCreated = true, isReceived = false)))
        repository.update(call(CallState.Ready(connection)))
        runCurrent()
    }

    @Test
    fun `the other side's data reaches the engine in the order it came, however long each packet takes`() =
        runTest {
            ready(manager())
            val session = engine.sessions.single()
            session.receiveDelayMillis = { if (it.first() == 1.toByte()) SLOW_PACKET_MILLIS else 0 }

            repository.signaling.emit(CallSignalingData(FakeCallRepository.STARTED_CALL_ID, byteArrayOf(1)))
            repository.signaling.emit(CallSignalingData(FakeCallRepository.STARTED_CALL_ID, byteArrayOf(2)))
            advanceTimeBy(SLOW_PACKET_MILLIS * 2)
            runCurrent()

            assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2)), session.received)
        }

    @Test
    fun `a session that connects after the call ended is closed, its microphone with it`() =
        runTest {
            engine.gate = CompletableDeferred()
            ready(manager())

            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = false)))
            runCurrent()
            engine.gate?.complete(Unit)
            runCurrent()

            assertTrue(engine.sessions.single().closed)
        }

    private companion object {
        /** How long the engine takes over a slow packet of the other side's data. */
        const val SLOW_PACKET_MILLIS = 100L
    }
}
