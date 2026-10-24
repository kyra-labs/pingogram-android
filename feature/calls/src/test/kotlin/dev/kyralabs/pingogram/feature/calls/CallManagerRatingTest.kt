package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Rating a call: asked when Telegram wants it for a call that lasted, kept until rated, declined or replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerRatingTest {
    private val repository = FakeCallRepository()
    private val engine = FakeCallEngine()
    private val clock = FakeCallClock()

    private fun TestScope.manager() =
        CallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            FakeCallTones(),
            clock,
            { "Ada" },
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

    private fun call(
        state: CallState,
        id: Int = FakeCallRepository.STARTED_CALL_ID,
    ) = Call(id, userId = 42, isOutgoing = true, isVideo = false, state = state)

    /** Calls out as call [id] and stays connected for [millis]. */
    private fun TestScope.connectedFor(
        manager: CallManager,
        millis: Long,
        id: Int = FakeCallRepository.STARTED_CALL_ID,
    ) {
        repository.startResult = Outcome.Success(id)
        backgroundScope.launchNow { manager.startCall(42) }
        repository.update(call(CallState.Pending(isCreated = true, isReceived = false), id))
        runCurrent()
        repository.update(call(CallState.Ready(connection), id))
        runCurrent()
        engine.sessions
            .last()
            .state.value = CallMediaState.Connected
        runCurrent()
        clock.now += millis
    }

    @Test
    fun `asks to rate a call Telegram wants rated, then rates it once`() =
        runTest {
            val manager = manager()
            connectedFor(manager, 30_000)

            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = true)))
            runCurrent()
            assertEquals(CallRatingRequest(7, 42, isVideo = false), manager.rating.value)

            manager.rate(2, setOf(CallProblem.Echo))
            manager.rate(5, emptySet())
            runCurrent()

            assertNull(manager.rating.value)
            assertEquals(listOf(FakeCallRepository.Rating(7, 2, setOf(CallProblem.Echo))), repository.rated)
        }

    @Test
    fun `does not ask when Telegram does not, the call was short, or it did not end normally`() =
        runTest {
            val manager = manager()
            connectedFor(manager, 30_000)
            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = false)))
            runCurrent()
            assertNull(manager.rating.value)
            advanceTimeBy(3.seconds)

            connectedFor(manager, 4_000, id = 8)
            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = true), 8))
            runCurrent()
            assertNull(manager.rating.value)
            advanceTimeBy(3.seconds)

            connectedFor(manager, 30_000, id = 9)
            repository.update(call(CallState.Discarded(CallDiscardReason.Disconnected, needsRating = true), 9))
            runCurrent()
            assertNull(manager.rating.value)
        }

    @Test
    fun `the rating question stays after the call clears, until dismissed or another call starts`() =
        runTest {
            val manager = manager()
            connectedFor(manager, 30_000)
            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = true)))
            runCurrent()
            advanceTimeBy(3.seconds)
            assertNull(manager.call.value)
            assertEquals(7, manager.rating.value?.callId)

            manager.dismissRating()
            assertNull(manager.rating.value)
            runCurrent()
            assertTrue(repository.rated.isEmpty())

            connectedFor(manager, 30_000, id = 8)
            repository.update(call(CallState.Discarded(CallDiscardReason.HungUp, needsRating = true), 8))
            runCurrent()
            assertEquals(8, manager.rating.value?.callId)
            advanceTimeBy(3.seconds)
            repository.startResult = Outcome.Success(9)
            backgroundScope.launchNow { manager.startCall(42) }
            runCurrent()
            assertNull(manager.rating.value)
        }
}
