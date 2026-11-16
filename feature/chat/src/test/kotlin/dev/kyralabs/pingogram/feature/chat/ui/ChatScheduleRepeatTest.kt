package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Who may repeat a scheduled message in [ChatViewModel] (td_api.tl `messageSchedulingStateSendAtDate.repeat_period`):
 * a Telegram Premium subscriber, where the chat lets them schedule; nobody else, and nobody in a secret chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatScheduleRepeatTest : ChatViewModelHarness() {
    private val ChatViewModel.canRepeat get() = (state.composer as ComposerUiState.Available).canRepeat

    @Test
    fun `a Telegram Premium subscriber may repeat what they schedule`() =
        runViewModelTest {
            users.setUsers(user(ME).copy(isPremium = true), user(ADA))

            assertTrue(showing(chat(), history(message(1))).canRepeat)
        }

    @Test
    fun `without Telegram Premium nothing repeats`() =
        runViewModelTest {
            users.setUsers(user(ME), user(ADA))

            assertFalse(showing(chat(), history(message(1))).canRepeat)
        }

    @Test
    fun `a secret chat, which schedules nothing, repeats nothing`() =
        runViewModelTest {
            users.setUsers(user(ME).copy(isPremium = true), user(ADA))
            secretChats.setSecretChats(ChatFixtures.secretChat())

            val secret = showing(chat(type = ChatType.Secret(secretChatId = 7, userId = ADA)), history(message(1)))

            assertFalse(secret.canRepeat)
        }
}
