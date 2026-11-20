package dev.kyralabs.pingogram.core.notifications

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [settled], which the share shortcuts wait on before working out their targets again (owner decision 2026-11-20):
 * the first value at once, then one rebuild for a burst of changes, once it ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettledFlowTest {
    private val delay = 2.seconds

    @Test
    fun `the first value is rebuilt at once, then a burst of changes once, after it settles`() =
        runTest {
            val changes = MutableSharedFlow<Int>()
            var rebuilds = 0
            val rebuilt = mutableListOf<Int>()
            backgroundScope.launch {
                changes
                    .settled(delay)
                    .map {
                        rebuilds++
                        it
                    }.collect { rebuilt += it }
            }
            runCurrent()

            changes.emit(0)
            runCurrent()
            assertEquals(listOf(0), rebuilt)

            // Ten changes, each well within the delay of the last.
            for (change in 1..10) {
                changes.emit(change)
                advanceTimeBy(100.milliseconds)
            }
            assertEquals(listOf(0), rebuilt)

            advanceTimeBy(delay)
            runCurrent()
            assertEquals(listOf(0, 10), rebuilt)
            assertEquals(2, rebuilds)
        }

    @Test
    fun `a new collection rebuilds its first value at once again`() =
        runTest {
            val changes = MutableSharedFlow<Int>()
            val settled = changes.settled(delay)
            val first = mutableListOf<Int>()
            val second = mutableListOf<Int>()
            val one = backgroundScope.launch { settled.collect { first += it } }
            runCurrent()
            changes.emit(1)
            runCurrent()
            one.cancel()

            backgroundScope.launch { settled.collect { second += it } }
            runCurrent()
            changes.emit(2)
            runCurrent()

            assertEquals(listOf(1), first)
            assertEquals(listOf(2), second)
        }
}
