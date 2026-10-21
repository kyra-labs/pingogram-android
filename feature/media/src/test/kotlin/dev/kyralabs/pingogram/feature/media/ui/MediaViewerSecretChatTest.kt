package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The media of a secret chat (`REQUIREMENTS.md` FR-E1) keeps the window secure in [MediaViewerViewModel], as its
 * conversation does; other chats' media does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerSecretChatTest : MediaViewerViewModelHarness() {
    @BeforeEach
    fun serveMedia() {
        media.serve(photos(1L..3L))
    }

    @Test
    fun `a secret chat's media asks for a secure window`() =
        runViewModelTest {
            chats.chats.value = mapOf(CHAT to chat(CHAT, "Ada").copy(type = ChatType.Secret(7, userId = 42)))
            val viewModel = viewModel(messageId = 2)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isSecretChat.collect {} }
            runCurrent()

            assertTrue(viewModel.isSecretChat.value)
        }

    @Test
    fun `other chats' media does not`() =
        runViewModelTest {
            chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines"))
            val viewModel = viewModel(messageId = 2)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isSecretChat.collect {} }
            runCurrent()

            assertFalse(viewModel.isSecretChat.value)
        }
}
