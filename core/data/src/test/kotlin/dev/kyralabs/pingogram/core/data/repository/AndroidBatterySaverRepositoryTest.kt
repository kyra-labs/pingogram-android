package dev.kyralabs.pingogram.core.data.repository

import android.os.PowerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [AndroidBatterySaverRepository]: one receiver for every collector, registered once and kept
 * (`docs/audit/performance-review-2026-11.md`, W6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidBatterySaverRepositoryTest {
    private val application = RuntimeEnvironment.getApplication()

    private fun receivers() =
        shadowOf(application).registeredReceivers.count {
            it.intentFilter.hasAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

    @Test
    fun `screens share one receiver, which the next screen takes up again`() =
        runTest {
            val repository = AndroidBatterySaverRepository(application, backgroundScope)
            val seen = mutableListOf<Boolean>()
            val collectors =
                List(3) {
                    launch(UnconfinedTestDispatcher(testScheduler)) { repository.isOn.collect { seen += it } }
                }
            runCurrent()

            assertEquals(1, receivers())
            assertEquals(List(3) { false }, seen)

            collectors.forEach { it.cancelAndJoin() }
            val next = launch(UnconfinedTestDispatcher(testScheduler)) { repository.isOn.collect { seen += it } }
            runCurrent()

            assertEquals(1, receivers())
            assertEquals(List(4) { false }, seen)
            next.cancelAndJoin()
        }
}
