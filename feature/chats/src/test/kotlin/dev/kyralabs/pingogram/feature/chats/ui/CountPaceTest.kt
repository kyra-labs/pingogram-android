package dev.kyralabs.pingogram.feature.chats.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil

/** [atCountPace]: the first value at once, then at most one every [COUNT_PACE], ending on the latest. */
@OptIn(ExperimentalCoroutinesApi::class)
class CountPaceTest {
    @Test
    fun `a burst of 100 changes shows the first at once, at most one a period, and ends on the last`() =
        runTest {
            val shown = shownAt(burst(CHANGES, every = STEP_MILLIS).atCountPace())

            val burstMillis = (CHANGES - 1) * STEP_MILLIS
            val most = ceil(burstMillis.toDouble() / COUNT_PACE.inWholeMilliseconds).toInt() + 1
            assertTrue(shown.size <= most, "${shown.size} values shown for at most $most: $shown")
            assertEquals(0 to 0L, shown.first())
            assertEquals(CHANGES - 1, shown.last().first)
            shown.zipWithNext().forEach { (a, b) ->
                assertTrue(b.second - a.second >= COUNT_PACE.inWholeMilliseconds, "$a then $b")
            }
        }

    @Test
    fun `a change after a quiet period shows at once`() =
        runTest {
            val upstream = MutableSharedFlow<Int>()
            val shown = mutableListOf<Pair<Int, Long>>()
            backgroundScope.launch {
                upstream.atCountPace().collect { shown += it to testScheduler.currentTime }
            }
            runCurrent()

            upstream.emit(1)
            advanceTimeBy(QUIET_MILLIS)
            upstream.emit(2)
            runCurrent()

            assertEquals(listOf(1 to 0L, 2 to QUIET_MILLIS), shown)
        }

    @Test
    fun `a change of shape shows at once within a period`() =
        runTest {
            val values = listOf(1 to "a", 2 to "a", 3 to "b", 4 to "b")
            val shown = shownAt(spaced(values).atCountPace { it.second })

            // 2 waits for its period and gives way to 3, a new shape, which shows at once; 4 waits for 3's period.
            val pace = COUNT_PACE.inWholeMilliseconds
            assertEquals(
                listOf(values[0] to 0L, values[2] to 2 * STEP_MILLIS, values[3] to 2 * STEP_MILLIS + pace),
                shown,
            )
        }

    @Test
    fun `the last value shows when the flow ends within a period`() =
        runTest {
            val shown = flowOf(1, 2, 3).atCountPace().toList()

            assertEquals(listOf(1, 3), shown)
        }

    private fun <T> flowOf(vararg values: T): Flow<T> = flow { values.forEach { emit(it) } }

    /** [values], one every [STEP_MILLIS] ms. */
    private fun <T> spaced(values: List<T>): Flow<T> =
        flow {
            values.forEachIndexed { index, value ->
                if (index > 0) delay(STEP_MILLIS)
                emit(value)
            }
        }

    /** [count] values, 0 first, one every [every] ms. */
    private fun burst(
        count: Int,
        every: Long,
    ): Flow<Int> =
        flow {
            repeat(count) {
                if (it > 0) delay(every)
                emit(it)
            }
        }

    /** What [flow] shows, each with the virtual time it showed at. */
    private suspend fun <T> TestScope.shownAt(flow: Flow<T>): List<Pair<T, Long>> {
        val shown = mutableListOf<Pair<T, Long>>()
        flow.onEach { shown += it to testScheduler.currentTime }.toList()
        advanceUntilIdle()
        return shown
    }

    private companion object {
        const val CHANGES = 100
        const val STEP_MILLIS = 10L
        const val QUIET_MILLIS = 1_000L
    }
}
