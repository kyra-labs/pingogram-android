package dev.kyralabs.pingogram.feature.chats.ui.archive

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettingsAvailability
import dev.kyralabs.pingogram.feature.chats.ui.FakeArchiveRowRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeArchiveSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveSettingsViewModelTest {
    private val repository = FakeArchiveSettingsRepository()
    private val rows = FakeArchiveRowRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ArchiveSettingsViewModel =
        ArchiveSettingsViewModel(repository, rows).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ArchiveSettingsViewModel): List<ArchiveSettingsEffect> {
        val effects = mutableListOf<ArchiveSettingsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ArchiveSettingsViewModel,
        event: ArchiveSettingsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val ArchiveSettingsViewModel.content
        get() = uiState.value.settings as ArchiveSettingsContent.Content

    @Test
    fun `loads while Telegram answers, then shows its settings and the row's style`() =
        runTest {
            val answer = CompletableDeferred<Outcome<ArchiveSettingsAvailability>>()
            repository.onRead = { answer.await() }
            rows.style.value = ArchiveRowStyle.Compact
            val viewModel = viewModel()

            assertEquals(ArchiveSettingsContent.Loading, viewModel.uiState.value.settings)
            assertEquals(ArchiveRowStyle.Compact, viewModel.uiState.value.rowStyle)

            answer.complete(Outcome.Success(AVAILABILITY))
            runCurrent()

            assertEquals(ArchiveSettingsContent.Content(SETTINGS, canArchiveAndMuteNewChats = true), viewModel.content)
        }

    @ParameterizedTest
    @MethodSource("errors")
    fun `a failed read shows its error, and trying again reads again`(error: DomainError) =
        runTest {
            repository.onRead = { Outcome.Failure(error) }
            val viewModel = viewModel()

            assertEquals(ArchiveSettingsContent.Error(error), viewModel.uiState.value.settings)

            repository.onRead = { Outcome.Success(AVAILABILITY) }
            send(viewModel, ArchiveSettingsEvent.Retry)

            assertEquals(2, repository.reads)
            assertEquals(SETTINGS, viewModel.content.settings)
        }

    @Test
    fun `trying again does nothing while the settings show`() =
        runTest {
            repository.settings = AVAILABILITY
            val viewModel = viewModel()

            send(viewModel, ArchiveSettingsEvent.Retry)

            assertEquals(1, repository.reads)
        }

    @Test
    fun `a flipped switch shows at once and is sent`() =
        runTest {
            repository.settings = AVAILABILITY
            val sent = CompletableDeferred<Outcome<Unit>>()
            repository.onSet = { sent.await() }
            val viewModel = viewModel()
            val changed = SETTINGS.copy(keepUnmutedChatsArchived = true)

            send(viewModel, ArchiveSettingsEvent.Change(changed))

            assertEquals(changed, viewModel.content.settings)
            assertEquals(listOf(changed), repository.changes)
            sent.complete(Outcome.Success(Unit))
            runCurrent()
            assertEquals(changed, viewModel.content.settings)
        }

    @ParameterizedTest
    @MethodSource("errors")
    fun `a refused change flips back and says why`(error: DomainError) =
        runTest {
            repository.settings = AVAILABILITY
            repository.onSet = { Outcome.Failure(error) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ArchiveSettingsEvent.Change(SETTINGS.copy(keepChatsFromFoldersArchived = false)))

            assertEquals(SETTINGS, viewModel.content.settings)
            assertEquals(listOf(ArchiveSettingsEffect.ShowError(error)), effects)
        }

    @Test
    fun `a refusal keeps a later change`() =
        runTest {
            repository.settings = AVAILABILITY
            val first = CompletableDeferred<Outcome<Unit>>()
            repository.onSet = { if (repository.changes.size == 1) first.await() else Outcome.Success(Unit) }
            val viewModel = viewModel()
            val second = SETTINGS.copy(keepUnmutedChatsArchived = true, keepChatsFromFoldersArchived = false)

            send(viewModel, ArchiveSettingsEvent.Change(SETTINGS.copy(keepUnmutedChatsArchived = true)))
            send(viewModel, ArchiveSettingsEvent.Change(second))
            first.complete(Outcome.Failure(DomainError.Network))
            runCurrent()

            assertEquals(second, viewModel.content.settings)
        }

    @Test
    fun `new chats cannot be archived where Telegram does not offer it, but can be let in again`() =
        runTest {
            repository.settings = ArchiveSettingsAvailability(ArchiveSettings(), canArchiveAndMuteNewChats = false)
            val viewModel = viewModel()

            assertEquals(false, viewModel.content.canChangeNewChats)
            send(viewModel, ArchiveSettingsEvent.Change(ArchiveSettings(archiveAndMuteNewChatsFromNonContacts = true)))

            assertTrue(repository.changes.isEmpty())

            repository.settings =
                ArchiveSettingsAvailability(
                    ArchiveSettings(archiveAndMuteNewChatsFromNonContacts = true),
                    canArchiveAndMuteNewChats = false,
                )
            val turnedOn = viewModel()
            assertEquals(true, turnedOn.content.canChangeNewChats)
            send(turnedOn, ArchiveSettingsEvent.Change(ArchiveSettings()))

            assertEquals(listOf(ArchiveSettings()), repository.changes)
        }

    @Test
    fun `an unchanged switch sends nothing`() =
        runTest {
            repository.settings = AVAILABILITY
            val viewModel = viewModel()

            send(viewModel, ArchiveSettingsEvent.Change(SETTINGS))

            assertTrue(repository.changes.isEmpty())
        }

    @Test
    fun `a picked row style is saved, and the page follows it`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, ArchiveSettingsEvent.SetRowStyle(ArchiveRowStyle.Hidden))

            assertEquals(listOf(ArchiveRowStyle.Hidden), rows.saved)
            assertEquals(ArchiveRowStyle.Hidden, viewModel.uiState.value.rowStyle)

            // The chat list's own menu changes it too.
            rows.style.value = ArchiveRowStyle.Full
            runCurrent()
            assertEquals(ArchiveRowStyle.Full, viewModel.uiState.value.rowStyle)
        }

    @Test
    fun `a row style that cannot be saved is a snackbar`() =
        runTest {
            val error = DomainError.Unknown(code = 0, message = "Disk full")
            rows.onSetStyle = { Outcome.Failure(error) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ArchiveSettingsEvent.SetRowStyle(ArchiveRowStyle.Compact))

            assertEquals(listOf(ArchiveSettingsEffect.ShowError(error)), effects)
            assertEquals(ArchiveRowStyle.Full, viewModel.uiState.value.rowStyle)
        }

    @Test
    fun `after process death everything is read again`() =
        runTest {
            repository.settings = AVAILABILITY
            viewModel()

            val restored = viewModel()

            assertEquals(2, repository.reads)
            assertEquals(SETTINGS, restored.content.settings)
        }

    private companion object {
        val SETTINGS =
            ArchiveSettings(
                archiveAndMuteNewChatsFromNonContacts = false,
                keepUnmutedChatsArchived = false,
                keepChatsFromFoldersArchived = true,
            )
        val AVAILABILITY = ArchiveSettingsAvailability(SETTINGS, canArchiveAndMuteNewChats = true)

        @JvmStatic
        fun errors() = listOf(DomainError.FloodWait(4), DomainError.Network, DomainError.Unauthorized)
    }
}
