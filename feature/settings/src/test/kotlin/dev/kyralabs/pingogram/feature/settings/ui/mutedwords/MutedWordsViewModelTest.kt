package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.testing.FakeMutedWordsRepository
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Muted words' page (FR-X21): the switch, adding and removing words, and errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class MutedWordsViewModelTest {
    private val repository = FakeMutedWordsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): MutedWordsViewModel {
        val viewModel = MutedWordsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: MutedWordsViewModel,
        vararg events: MutedWordsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    @Test
    fun `nothing shows until the settings are read, then off without words`() =
        runTest {
            assertNull(MutedWordsViewModel(repository).uiState.value.settings)

            assertEquals(MutedWordSettings(), viewModel().uiState.value.settings)
        }

    @Test
    fun `words are added while off, the field empties, and a tap removes one`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<MutedWordsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, MutedWordsEvent.Add("spoiler"), MutedWordsEvent.Add("crypto"), MutedWordsEvent.Add("  "))

            assertEquals(
                listOf("spoiler", "crypto"),
                viewModel.uiState.value.settings
                    ?.words,
            )
            assertEquals(listOf(MutedWordsEffect.Added, MutedWordsEffect.Added), effects)
            assertFalse(
                viewModel.uiState.value.settings!!
                    .enabled,
            )

            send(viewModel, MutedWordsEvent.Remove("spoiler"))

            assertEquals(
                listOf("crypto"),
                viewModel.uiState.value.settings
                    ?.words,
            )
        }

    @Test
    fun `the switch turns muting on and off`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, MutedWordsEvent.SetEnabled(true))
            assertTrue(
                viewModel.uiState.value.settings!!
                    .enabled,
            )

            send(viewModel, MutedWordsEvent.SetEnabled(false))
            assertFalse(
                viewModel.uiState.value.settings!!
                    .enabled,
            )
        }

    @Test
    fun `no more words once there are a hundred`() =
        runTest {
            repository.settings.value =
                MutedWordSettings(words = persistentListOf(*Array(MutedWordSettings.MAX_WORDS) { "word$it" }))

            assertFalse(viewModel().uiState.value.canAdd)
        }

    @Test
    fun `a change that is not saved is a snackbar`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<MutedWordsEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            val error = DomainError.Unknown(code = 0, message = "disk full")
            repository.failure = error

            send(viewModel, MutedWordsEvent.SetEnabled(true))

            assertEquals(listOf(MutedWordsEffect.ShowError(error)), effects)
        }
}
