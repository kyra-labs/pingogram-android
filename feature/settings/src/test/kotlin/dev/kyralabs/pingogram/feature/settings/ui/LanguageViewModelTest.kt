package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.core.testing.FakeAppLanguageRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {
    private val repository = FakeAppLanguageRepository(supported = listOf("en", "de", "pt-BR"))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The view model, with the page showing: its state is read only while something collects it. */
    private fun TestScope.viewModel(repository: AppLanguageRepository = this@LanguageViewModelTest.repository) =
        LanguageViewModel(repository).also { show(it) }

    private fun TestScope.show(viewModel: LanguageViewModel): Job =
        backgroundScope.launch { viewModel.uiState.collect {} }.also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: LanguageViewModel): List<LanguageEffect> {
        val effects = mutableListOf<LanguageEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: LanguageViewModel,
        event: LanguageEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val LanguageViewModel.state get() = uiState.value

    private fun content(selected: String?) = LanguageUiState.Content(persistentListOf("en", "de", "pt-BR"), selected)

    @Test
    fun `is loading until the languages are read`() =
        runTest {
            assertEquals(LanguageUiState.Loading, LanguageViewModel(repository).state)
        }

    @Test
    fun `shows the shipped languages, the system's selected until another is chosen`() =
        runTest {
            assertEquals(content(selected = null), viewModel().state)
        }

    @Test
    fun `choosing a language saves it and selects it`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, LanguageEvent.Select("de"))

            assertEquals("de", repository.language.value)
            assertEquals(content(selected = "de"), viewModel.state)
        }

    @Test
    fun `choosing the system default saves no language`() =
        runTest {
            repository.language.value = "pt-BR"
            val viewModel = viewModel()

            send(viewModel, LanguageEvent.Select(null))

            assertNull(repository.language.value)
            assertEquals(content(selected = null), viewModel.state)
        }

    @Test
    fun `a regional variant chosen in Android's settings selects the language it belongs to`() =
        runTest {
            repository.language.value = "de-AT"

            assertEquals(content(selected = "de"), viewModel().state)
        }

    @Test
    fun `a language Pingogram does not ship selects nothing but the system default`() =
        runTest {
            repository.language.value = "fr"

            assertEquals(content(selected = null), viewModel().state)
        }

    @Test
    fun `the choice is read again when the page shows again`() =
        runTest {
            val androidSettings = AndroidSettingsLanguage()
            val viewModel = LanguageViewModel(androidSettings)
            val showing = show(viewModel)
            assertEquals(LanguageUiState.Content(persistentListOf("en", "de"), null), viewModel.state)

            showing.cancel()
            androidSettings.languageTag = "de"
            show(viewModel)

            assertEquals(LanguageUiState.Content(persistentListOf("en", "de"), "de"), viewModel.state)
        }

    @Test
    fun `a language that cannot be saved shows the error and changes nothing`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val error = DomainError.Unknown(0, "Disk full")
            repository.failure = error

            send(viewModel, LanguageEvent.Select("de"))

            assertEquals(listOf(LanguageEffect.ShowError(error)), effects)
            assertEquals(content(selected = null), viewModel.state)
        }

    @Test
    fun `a saved language shows no message`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, LanguageEvent.Select("de"))

            assertEquals(emptyList<LanguageEffect>(), effects)
        }

    @Test
    fun `a shipped language is the tag itself or the one it is a variant of`() {
        val languages = listOf("en", "pt-BR")

        assertEquals("en", shippedLanguageOf("en", languages))
        assertEquals("en", shippedLanguageOf("en-GB", languages))
        assertEquals("pt-BR", shippedLanguageOf("pt-PT", languages))
        assertNull(shippedLanguageOf("ja", languages))
        assertNull(shippedLanguageOf(null, languages))
    }

    /** The language as Android 13+ keeps it: changed in Android's settings without telling the app. */
    private class AndroidSettingsLanguage : AppLanguageRepository {
        var languageTag: String? = null

        override suspend fun supportedLanguages(): List<String> = listOf("en", "de")

        override val language: Flow<String?> = flow { emit(languageTag) }

        override suspend fun setLanguage(languageTag: String?): Outcome<Unit> {
            this.languageTag = languageTag
            return Outcome.Success(Unit)
        }
    }
}
