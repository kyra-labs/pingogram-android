package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.testing.FakeBackupSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [SignInRestoreOffer]: a login asked for and completed offers the restore card; an account that starts ready not. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInRestoreOfferTest {
    private val settings = FakeBackupSettingsRepository()
    private val first = MutableStateFlow<AuthStep>(AuthStep.Initializing)
    private val added = MutableStateFlow<AuthStep>(AuthStep.Initializing)
    private val accounts = MutableStateFlow(mapOf(0 to first))

    private fun TestScope.start() {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        SignInRestoreOffer(accounts, settings, scope).start()
    }

    @Test
    fun `an account that starts ready offers nothing`() =
        runTest {
            start()
            first.value = AuthStep.Ready

            assertEquals(false, settings.restorePromptPending.value)
        }

    @Test
    fun `a sign-in offers the restore card, as does an account added later`() =
        runTest {
            start()
            first.value = AuthStep.WaitPhoneNumber
            first.value = AuthStep.Ready
            assertEquals(true, settings.restorePromptPending.value)

            settings.dismissRestorePrompt()
            accounts.value = mapOf(0 to first, 1 to added)
            added.value = AuthStep.WaitPhoneNumber
            added.value = AuthStep.Ready
            assertEquals(true, settings.restorePromptPending.value)
        }
}
