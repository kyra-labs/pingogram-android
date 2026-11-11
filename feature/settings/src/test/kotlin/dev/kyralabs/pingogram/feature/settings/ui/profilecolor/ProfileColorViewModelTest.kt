package dev.kyralabs.pingogram.feature.settings.ui.profilecolor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.data.repository.TdlibAccentColorRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ProfileColorViewModel] on the real repositories: the colors, choosing, saving, a refusal and process death. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileColorViewModelTest {
    private val client = FakeTdlibClient()
    private val created = mutableListOf<ProfileColorViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ProfileColorViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val colors = TdlibAccentColorRepository(client, backgroundScope)
        advanceUntilIdle()
        client.emit(TdApi.UpdateOption("my_id", TdApi.OptionValueInteger(ME)))
        client.emit(TdApi.UpdateUser(TdApiFixtures.user(id = ME, firstName = "Yam").apply { accentColorId = 5 }))
        client.emit(
            TdApi.UpdateAccentColors(
                arrayOf(TdApi.AccentColor(9, 4, intArrayOf(), intArrayOf(), 0)),
                intArrayOf(0, 1, 2, 3, 4, 5, 6, 9),
            ),
        )
        val viewModel = ProfileColorViewModel(handle, users, colors).also { created += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private val ProfileColorViewModel.content get() = uiState.value as ProfileColorUiState.Content

    @Test
    fun `shows the user's color, the built-in ones to pick and Telegram's Premium ones apart`() =
        runTest {
            with(viewModel().content) {
                assertEquals("Yam", name)
                assertEquals(5, current)
                assertEquals(5, chosen)
                assertEquals((0..6).toList(), colors.map { it.id })
                assertEquals(listOf(9), premiumColors.map { it.id })
                assertFalse(canSave)
            }
        }

    @Test
    fun `a Premium color cannot be chosen, a built-in one is saved and closes the page`() =
        runTest {
            client.on(TdApi.SetAccentColor::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileColorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(ProfileColorEvent.Choose(9))
            advanceUntilIdle()
            assertEquals(5, viewModel.content.chosen)
            viewModel.onEvent(ProfileColorEvent.Choose(1))
            advanceUntilIdle()
            assertTrue(viewModel.content.canSave)
            viewModel.onEvent(ProfileColorEvent.Save)
            advanceUntilIdle()

            assertEquals(1, client.sentOf<TdApi.SetAccentColor>().single().accentColorId)
            assertEquals(listOf<ProfileColorEffect>(ProfileColorEffect.Saved), effects)
        }

    @Test
    fun `Telegram's refusal is shown and the choice stays`() =
        runTest {
            client.on(TdApi.SetAccentColor::class, TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")))
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileColorEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
            viewModel.onEvent(ProfileColorEvent.Choose(2))
            advanceUntilIdle()
            viewModel.onEvent(ProfileColorEvent.Save)
            advanceUntilIdle()

            assertTrue(effects.single() is ProfileColorEffect.ShowError)
            assertEquals(2, viewModel.content.chosen)
            assertFalse(viewModel.content.isSaving)
        }

    @Test
    fun `the color chosen survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            viewModel(handle).onEvent(ProfileColorEvent.Choose(3))
            advanceUntilIdle()
            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
            assertEquals(3, restored.content.chosen)
        }

    private companion object {
        const val ME = 7L
    }
}
