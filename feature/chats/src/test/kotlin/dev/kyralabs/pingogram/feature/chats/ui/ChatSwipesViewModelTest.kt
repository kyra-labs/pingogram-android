package dev.kyralabs.pingogram.feature.chats.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The chat list's swipes follow the device's settings, read and archive until they are read. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSwipesViewModelTest {
    private val settings = FakeChatListSettingsRepository()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `follows the chosen swipes`() =
        runTest {
            ChatSwipesViewModel(settings).swipes.test {
                assertEquals(ChatSwipes(SwipeAction.Read, SwipeAction.Archive), awaitItem())

                settings.settings.value = ChatListSettings(startSwipe = SwipeAction.Pin, endSwipe = SwipeAction.None)
                assertEquals(ChatSwipes(SwipeAction.Pin, SwipeAction.None), awaitItem())
            }
        }

    @Test
    fun `follows whether swiping the list changes the folder`() =
        runTest {
            ChatSwipesViewModel(settings).swipes.test {
                assertEquals(false, awaitItem().changesFolder)

                settings.settings.value = ChatListSettings(swipeChangesFolder = true)
                assertEquals(ChatSwipes(changesFolder = true), awaitItem())
            }
        }
}
