package dev.kyralabs.pingogram.feature.chats.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The chat list's counts at a pace the eye can follow (owner decision 2026-11-21): the first value at once, then at
 * most one every [period], always ending on the latest. While TDLib catches up after a start, the counts change many
 * times a second for seconds, and each change draws a frame and lays out its text (`docs/PERFORMANCE.md` §4).
 *
 * A value whose [shapeOf] differs from the last one's (a tab added, removed or renamed, not a count) follows at once,
 * so what the counts sit on never waits for them.
 */
internal fun <T> Flow<T>.atCountPace(
    period: Duration = COUNT_PACE,
    shapeOf: (T) -> Any? = {},
): Flow<T> =
    flow {
        coroutineScope {
            val values = Channel<Latest<T>>(Channel.CONFLATED)
            launch {
                collect { values.send(Latest(it)) }
                values.close()
            }
            // Each period is numbered, so the end of one cut short is not taken for the end of the next.
            val periodEnds = Channel<Int>(Channel.UNLIMITED)
            var window: Job? = null
            var periods = 0
            var shape: Any? = null
            var waiting: Latest<T>? = null

            suspend fun show(value: T) {
                emit(value)
                shape = shapeOf(value)
                waiting = null
                window?.cancel()
                val number = ++periods
                window =
                    launch {
                        delay(period)
                        periodEnds.send(number)
                    }
            }

            var collecting = true
            while (collecting) {
                select {
                    values.onReceiveCatching { result ->
                        val latest = result.getOrNull()
                        when {
                            latest == null -> {
                                collecting = false
                                // The last value still waits for its period.
                                waiting?.let { last ->
                                    window?.join()
                                    emit(last.value)
                                }
                            }

                            window?.isActive != true || shapeOf(latest.value) != shape -> {
                                show(latest.value)
                            }

                            else -> {
                                waiting = latest
                            }
                        }
                    }
                    periodEnds.onReceive { number ->
                        val next = waiting
                        if (number == periods && next != null) show(next.value)
                    }
                }
            }
        }
    }

/** A value as it came, so a null value is told apart from none. */
private class Latest<T>(
    val value: T,
)

/** How often a count on the chat list may change (owner decision 2026-11-21). */
internal val COUNT_PACE = 250.milliseconds
