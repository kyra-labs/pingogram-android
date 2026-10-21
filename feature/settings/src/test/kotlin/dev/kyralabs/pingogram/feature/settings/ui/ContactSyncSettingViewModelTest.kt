package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PhoneContacts
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncDialog
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEffect
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ContactSyncSettingViewModel], "Sync contacts" in Privacy and security, on a fake repository: the switch follows
 * sync, turning it on explains and asks first, turning it off asks first, the page showing reads the permission
 * again, and errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactSyncSettingViewModelTest {
    private val repository = FakeContactSyncRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ContactSyncSettingViewModel {
        val viewModel = ContactSyncSettingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.status.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ContactSyncSettingViewModel): List<ContactSyncEffect> {
        val effects = mutableListOf<ContactSyncEffect>()
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { viewModel.contactSync.effects.toList(effects) }
        return effects
    }

    @Test
    fun `the switch follows where sync is`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ContactSyncStatus.Off, viewModel.status.value)

            repository.status.value = ContactSyncStatus.NeedsPermission
            runCurrent()

            assertEquals(ContactSyncStatus.NeedsPermission, viewModel.status.value)
        }

    @Test
    fun `turning it on explains first and asks Android, then turns sync on`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onCheckedChange(true)
            assertEquals(ContactSyncDialog.Rationale, viewModel.contactSync.controls.value.dialog)
            viewModel.contactSync.onEvent(ContactSyncEvent.Continue)
            viewModel.contactSync.onEvent(ContactSyncEvent.PermissionResult(granted = true, canAskAgain = true))
            runCurrent()

            assertEquals(listOf<ContactSyncEffect>(ContactSyncEffect.RequestPermission), effects)
            assertEquals(listOf("turnOn"), repository.calls)
        }

    @Test
    fun `turning it off asks first`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onCheckedChange(false)
            assertEquals(ContactSyncDialog.TurnOff, viewModel.contactSync.controls.value.dialog)
            viewModel.contactSync.onEvent(ContactSyncEvent.ConfirmTurnOff)
            runCurrent()

            assertEquals(listOf("turnOff"), repository.calls)
            assertEquals(listOf<ContactSyncEffect>(ContactSyncEffect.TurnedOff), effects)
        }

    @Test
    fun `the page showing again reads the permission again`() =
        runTest {
            viewModel().onStarted()

            assertEquals(listOf("refreshPermission"), repository.calls)
        }

    @Test
    fun `failures are snackbars, flood waits, network and unauthorized alike`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val errors = listOf(DomainError.FloodWait(5), DomainError.Network, DomainError.Unauthorized)

            errors.forEach { error ->
                repository.outcome = Outcome.Failure(error)
                viewModel.onCheckedChange(false)
                viewModel.contactSync.onEvent(ContactSyncEvent.ConfirmTurnOff)
                runCurrent()
            }

            assertEquals(errors.map { ContactSyncEffect.ShowError(it) }, effects)
        }

    private class FakeContactSyncRepository : ContactSyncRepository {
        override val status = MutableStateFlow<ContactSyncStatus>(ContactSyncStatus.Off)
        override val settings = MutableStateFlow(ContactSyncSettings())
        override val phoneContacts = MutableStateFlow<PhoneContacts?>(null)
        val calls = mutableListOf<String>()
        var outcome: Outcome<Unit> = Outcome.Success(Unit)

        override fun refreshPermission() {
            calls += "refreshPermission"
        }

        override suspend fun turnOn(): Outcome<Unit> = record("turnOn")

        override suspend fun turnOff(): Outcome<Unit> = record("turnOff")

        override suspend fun syncIfDue(): Outcome<Unit> = record("syncIfDue")

        override suspend fun syncNow(): Outcome<Unit> = record("syncNow")

        override suspend fun dismissPrompt(): Outcome<Unit> = record("dismissPrompt")

        private fun record(call: String): Outcome<Unit> {
            calls += call
            return outcome
        }
    }
}
