package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The share button beside channel posts in [ChatViewModel] (`docs/DESIGN_SYSTEM.md` §6 "Share button"): which bubbles
 * have it, and the chats that let it forward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatShareButtonTest : ChatViewModelHarness() {
    @Test
    fun `a channel's posts have the share button, and the channel lets it forward`() =
        runViewModelTest {
            groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(9))
            val post = message(1, CHANNEL_CHAT).copy(isChannelPost = true)
            val viewModel = showing(channelChat, history(post))

            assertTrue(viewModel.bubble(1).hasShareButton)
            assertTrue(viewModel.state.shortcuts.canForward)
        }

    @Test
    fun `a post forwarded from a channel has the share button, other messages do not`() =
        runViewModelTest {
            val fromChannel = ForwardInfo(MessageOrigin.Channel(CHANNEL_CHAT, messageId = 5, authorSignature = ""), 0)
            val fromUser = ForwardInfo(MessageOrigin.User(ADA), 0)
            val viewModel =
                showing(
                    chat(),
                    history(message(1, forwardInfo = fromChannel), message(2, forwardInfo = fromUser), message(3)),
                )

            assertEquals(listOf(true, false, false), (1L..3L).map { viewModel.bubble(it).hasShareButton })
        }

    @Test
    fun `a chat that protects its content lets no share button forward`() =
        runViewModelTest {
            val viewModel = showing(channelChat.copy(hasProtectedContent = true), history(message(1, CHANNEL_CHAT)))

            assertFalse(viewModel.state.shortcuts.canForward)
        }

    @Test
    fun `a secret chat lets no share button forward`() =
        runViewModelTest {
            val secret = chat(id = SECRET, type = ChatType.Secret(secretChatId = 7, userId = ADA))
            val viewModel = showing(secret, history(message(1, SECRET)))

            assertFalse(viewModel.state.shortcuts.canForward)
        }

    private companion object {
        const val SECRET = 4_000L
    }
}
