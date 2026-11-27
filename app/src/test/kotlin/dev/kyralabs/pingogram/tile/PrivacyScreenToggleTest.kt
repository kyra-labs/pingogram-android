package dev.kyralabs.pingogram.tile

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.testing.FakePrivacyScreenRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The privacy screen's tile turns the same switch as Privacy and security, the other way each tap (FR-X25). */
class PrivacyScreenToggleTest {
    @Test
    fun `a tap turns it on, and the next one off`() =
        runTest {
            val repository = FakePrivacyScreenRepository()
            val toggle = PrivacyScreenToggle(repository)

            assertEquals(Outcome.Success(Unit), toggle.toggle())
            assertTrue(repository.enabled.value)

            toggle.toggle()
            assertFalse(repository.enabled.value)
        }

    @Test
    fun `turning it off on a locked phone needs unlocking first, turning it on never does`() =
        runTest {
            val repository = FakePrivacyScreenRepository()
            val toggle = PrivacyScreenToggle(repository)

            assertFalse(toggle.needsUnlock(isLocked = true))

            repository.enabled.value = true
            assertTrue(toggle.needsUnlock(isLocked = true))
            assertFalse(toggle.needsUnlock(isLocked = false))
        }
}
