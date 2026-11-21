package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A chat's own wallpaper: set for the user alone or both people, dimmed, reset, and failures that change nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOwnWallpaperViewModelTest {
    private val wallpapers = FakeWallpaperRepository(installedWallpapers = listOf(PHOTO))
    private val users = PremiumUsers(ME)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val photos = FakePickedPhotoRepository()

    private fun viewModel() = ChatOwnWallpaperViewModel(CHAT, wallpapers, users, photos)

    private fun TestScope.effectsOf(viewModel: ChatOwnWallpaperViewModel): List<ChatOwnWallpaperEffect> {
        val effects = mutableListOf<ChatOwnWallpaperEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `sets a pick for the user alone, with its dimming`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(ChatOwnWallpaperEvent.SetDimming(40))
            viewModel.onWallpaperEvent(WallpaperEvent.Pick(PHOTO))
            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))

            val set = wallpapers.chatWallpapersSet.single()
            assertEquals(CHAT, set.chatId)
            assertEquals(40, set.darkThemeDimming)
            assertEquals(true, set.onlyForSelf)
            assertEquals(ChatWallpaperOverride(PHOTO, 40), viewModel.uiState.value.own)
            assertNull(viewModel.uiState.value.picker.pending)
        }

    @Test
    fun `a new wallpaper dims by 30 percent in dark theme until the user changes it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onWallpaperEvent(WallpaperEvent.Pick(PHOTO))
            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))

            assertEquals(30, wallpapers.chatWallpapersSet.single().darkThemeDimming)
        }

    @Test
    fun `a photo is prepared at the screen's size, up to 2560 px`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onWallpaperEvent(WallpaperEvent.PickPhoto("content://phone", SCREEN_SIDE))
            viewModel.onWallpaperEvent(WallpaperEvent.Cancel)
            viewModel.onWallpaperEvent(WallpaperEvent.PickPhoto("content://tablet", 3200))

            assertEquals(listOf(SCREEN_SIDE, 2560), photos.preparedSides)
        }

    @Test
    fun `only Telegram Premium sets it for both people`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(ChatOwnWallpaperEvent.SetForBoth(true))
            assertFalse(viewModel.uiState.value.forBoth)

            users.premium.value = true
            assertEquals(true, viewModel.uiState.value.canSetForBoth)
            assertEquals(true, viewModel.uiState.value.forBoth)
            viewModel.onWallpaperEvent(WallpaperEvent.Pick(PHOTO))
            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))

            assertEquals(false, wallpapers.chatWallpapersSet.single().onlyForSelf)
        }

    @Test
    fun `shows the chat's own wallpaper and its dimming, and resets it`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 25))
            val viewModel = viewModel()
            assertEquals(25, viewModel.uiState.value.dimming)

            viewModel.onEvent(ChatOwnWallpaperEvent.Reset)

            assertNull(viewModel.uiState.value.own)
        }

    @Test
    fun `the slider previews the dimming, and the chat's own wallpaper keeps it once it settles`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 30))
            val viewModel = viewModel()

            viewModel.onEvent(ChatOwnWallpaperEvent.SetDimming(50))
            viewModel.onEvent(ChatOwnWallpaperEvent.SetDimming(60))
            assertEquals(60, viewModel.uiState.value.dimming)
            assertEquals(emptyList<Any>(), wallpapers.chatWallpapersSet)

            viewModel.onEvent(ChatOwnWallpaperEvent.SaveDimming(emptyList()))

            val set = wallpapers.chatWallpapersSet.single()
            assertEquals(WallpaperChoice.Existing(PHOTO), set.choice)
            assertEquals(60, set.darkThemeDimming)
            assertEquals(true, set.onlyForSelf)
            assertEquals(ChatWallpaperOverride(PHOTO, 60), viewModel.uiState.value.own)
        }

    @Test
    fun `a dimming that cannot be saved goes back to the chat's and says why`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 30))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(ChatOwnWallpaperEvent.SetDimming(70))
            wallpapers.failure = DomainError.Network

            viewModel.onEvent(ChatOwnWallpaperEvent.SaveDimming(emptyList()))

            assertEquals(listOf(ChatOwnWallpaperEffect.ShowError(DomainError.Network)), effects)
            assertEquals(30, viewModel.uiState.value.dimming)
        }

    @Test
    fun `while a pick waits, the dimming goes with it when applied, not before`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 30))
            val viewModel = viewModel()
            viewModel.onWallpaperEvent(WallpaperEvent.Pick(PHOTO))
            viewModel.onEvent(ChatOwnWallpaperEvent.SetDimming(80))

            viewModel.onEvent(ChatOwnWallpaperEvent.SaveDimming(emptyList()))
            assertEquals(emptyList<Any>(), wallpapers.chatWallpapersSet)

            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))
            assertEquals(80, wallpapers.chatWallpapersSet.single().darkThemeDimming)
        }

    @Test
    fun `a failed upload keeps the wallpaper there was and says why`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 0))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            viewModel.onWallpaperEvent(WallpaperEvent.PickPhoto("content://photo", SCREEN_SIDE))
            wallpapers.failure = DomainError.Network

            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))

            assertEquals(listOf(ChatOwnWallpaperEffect.ShowError(DomainError.Network)), effects)
            assertEquals(
                PHOTO,
                viewModel.uiState.value.own
                    ?.wallpaper,
            )
            assertEquals(true, viewModel.uiState.value.picker.pending is WallpaperChoice.NewPhoto)
        }

    private companion object {
        const val CHAT = 7L
        const val ME = 1L
        const val SCREEN_SIDE = 2400
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
    }
}
