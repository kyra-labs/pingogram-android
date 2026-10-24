package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.PowerSaving
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.testing.FakeBatterySaverRepository
import dev.kyralabs.pingogram.core.testing.FakePowerSavingRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultPowerSavingPolicyTest {
    private val settings = FakePowerSavingRepository()
    private val batterySaver = FakeBatterySaverRepository()
    private val policy = DefaultPowerSavingPolicy(settings, batterySaver)

    @Test
    fun `follows Battery Saver by default`() =
        runTest {
            policy.inEffect.test {
                assertEquals(PowerSaving.None, awaitItem())

                batterySaver.isOn.value = true
                assertEquals(PowerSaving(PowerSavingPart.entries.toSet()), awaitItem())

                batterySaver.isOn.value = false
                assertEquals(PowerSaving.None, awaitItem())
            }
        }

    @Test
    fun `turns off only the parts the user chose`() =
        runTest {
            settings.setMode(PowerSavingMode.Always)
            settings.setPart(PowerSavingPart.Autoplay, off = false)

            policy.inEffect.test {
                val inEffect = awaitItem()
                assertEquals(true, inEffect.stillStickers)
                assertEquals(false, inEffect.noAutoplay)
                assertEquals(true, inEffect.stillCalls)
            }
        }

    @Test
    fun `off ignores Battery Saver`() =
        runTest {
            settings.setMode(PowerSavingMode.Off)
            batterySaver.isOn.value = true

            policy.inEffect.test { assertEquals(PowerSaving.None, awaitItem()) }
        }
}
