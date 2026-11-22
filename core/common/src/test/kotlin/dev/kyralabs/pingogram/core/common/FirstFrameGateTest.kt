package dev.kyralabs.pingogram.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FirstFrameGateTest {
    @Test
    fun `holds work back until the first frame`() =
        runTest {
            val gate = FirstFrameGate()
            val waiting = async { gate.await() }
            advanceTimeBy(1_000.milliseconds)
            assertFalse(waiting.isCompleted)

            gate.open()
            runCurrent()

            assertTrue(waiting.isCompleted)
        }

    @Test
    fun `lets work go ahead in a process that draws no frame, then stays open`() =
        runTest {
            val gate = FirstFrameGate()
            gate.await()
            gate.await()

            assertEquals(FirstFrameGate.FALLBACK.inWholeMilliseconds, testScheduler.currentTime)
        }
}
