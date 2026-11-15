package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tapping "Forwarded from …" in [ChatViewModel] ([ChatEvent.OpenForwardSource]): a user's profile, a chat, a channel's
 * post (in place when it is this chat's), and a snackbar for someone who hides their account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatForwardSourceTest : ChatViewModelHarness() {
    private fun forwarded(
        id: Long,
        origin: MessageOrigin,
    ) = message(id, forwardInfo = ForwardInfo(origin, date = 0))

    @Test
    fun `each forwarded bubble knows where it comes from`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1),
                        forwarded(2, MessageOrigin.User(GRACE)),
                        forwarded(3, MessageOrigin.HiddenUser("Anonymous")),
                        forwarded(4, MessageOrigin.Chat(GROUP, authorSignature = "")),
                        forwarded(5, MessageOrigin.Channel(CHANNEL, messageId = 9, authorSignature = "Ed")),
                    ),
                )

            assertNull(viewModel.bubble(1).forwardSource)
            assertEquals(ForwardSource.Profile(GRACE), viewModel.bubble(2).forwardSource)
            assertEquals(ForwardSource.Hidden, viewModel.bubble(3).forwardSource)
            assertEquals(ForwardSource.Chat(GROUP, messageId = null), viewModel.bubble(4).forwardSource)
            assertEquals(ForwardSource.Chat(CHANNEL, messageId = 9), viewModel.bubble(5).forwardSource)
        }

    @Test
    fun `a user's forward opens their profile`() =
        runTest {
            val viewModel = showing(chat(), history(forwarded(1, MessageOrigin.User(GRACE))))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenForwardSource(ForwardSource.Profile(GRACE)))
                assertEquals(ChatEffect.OpenProfile(GRACE), awaitItem())
            }
        }

    @Test
    fun `a channel's post opens at the original message, a chat without one at its end`() =
        runTest {
            val viewModel = showing(chat(), history(message(1)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenForwardSource(ForwardSource.Chat(CHANNEL, messageId = 9)))
                assertEquals(ChatEffect.OpenMessage(CHANNEL, 9), awaitItem())
                send(viewModel, ChatEvent.OpenForwardSource(ForwardSource.Chat(GROUP, messageId = null)))
                assertEquals(ChatEffect.OpenChat(GROUP), awaitItem())
            }
        }

    @Test
    fun `a post forwarded from this chat is shown in place`() =
        runTest {
            val viewModel =
                showing(chat(), history(message(1), forwarded(2, MessageOrigin.Channel(PRIVATE_CHAT, 1, ""))))
            val before = viewModel.anchor.id

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenForwardSource(ForwardSource.Chat(PRIVATE_CHAT, messageId = 1)))
                expectNoEvents()
            }
            assertEquals(ListAnchor(before + 1, AnchorTarget.Message(1)), viewModel.anchor)
        }

    @Test
    fun `someone who hides their account opens nothing, and a snackbar says so`() =
        runTest {
            val viewModel = showing(chat(), history(forwarded(1, MessageOrigin.HiddenUser("Anonymous"))))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenForwardSource(ForwardSource.Hidden))
                assertEquals(ChatEffect.ForwardSourceHidden, awaitItem())
            }
        }

    private companion object {
        const val GROUP = -200L
        const val CHANNEL = -300L
    }
}
