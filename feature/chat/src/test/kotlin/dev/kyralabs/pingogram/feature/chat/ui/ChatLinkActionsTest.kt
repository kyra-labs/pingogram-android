package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An actions sheet of message [messageId], with nothing in it but the pressed [link]. */
internal fun actionsSheet(
    messageId: Long,
    link: String? = null,
) = MessageActionsSheet(messageId, messageId, persistentListOf(), persistentListOf(), link = link)

/**
 * [ChatViewModel]'s message actions for a long press on a link of a message's text (`REQUIREMENTS.md` FR-C6): the
 * sheet offers "Open link" and "Copy link" for it first, which open it or put it on the clipboard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLinkActionsTest : ChatViewModelHarness() {
    private fun TestScope.privateChat(): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
        chats.setChats(chat())
        messages.setHistory(PRIVATE_CHAT, history(outgoing(1), message(2)))
        return viewModel()
    }

    @Test
    fun `a long press on a link offers to open and copy it, before the message's actions`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, ChatEvent.ShowMessageActions(2, link = URL))

            val sheet = viewModel.state.messageActions!!
            assertEquals(URL, sheet.link)
            assertEquals(listOf(MessageAction.OpenLink, MessageAction.CopyPressedLink), sheet.actions.take(2))
            assertTrue(MessageAction.Reply in sheet.actions.drop(2))
        }

    @Test
    fun `a long press elsewhere offers no link`() =
        runViewModelTest {
            val viewModel = privateChat()

            send(viewModel, ChatEvent.ShowMessageActions(2))

            val sheet = viewModel.state.messageActions!!
            assertNull(sheet.link)
            assertFalse(sheet.actions.any { it.isLinkAction })
        }

    @Test
    fun `open link opens it, and copy link copies it, closing the sheet`() =
        runViewModelTest {
            val viewModel = privateChat()

            viewModel.effects.test {
                send(viewModel, ChatEvent.ShowMessageActions(2, link = URL))
                send(viewModel, MessageAction.OpenLink.eventFor(viewModel.state.messageActions!!))
                assertEquals(ChatEffect.OpenUrl(URL), awaitItem())
                assertNull(viewModel.state.messageActions)

                send(viewModel, ChatEvent.ShowMessageActions(2, link = URL))
                send(viewModel, MessageAction.CopyPressedLink.eventFor(viewModel.state.messageActions!!))
                assertEquals(ReplyEffect.CopyLink(URL), awaitItem())
                assertNull(viewModel.state.messageActions)
            }
        }

    @Test
    fun `the message's own link reads copy message link beside the pressed one`() {
        assertEquals(R.string.message_action_copy_link, MessageAction.CopyPressedLink.labelWith(URL))
        assertEquals(R.string.message_action_copy_message_link, MessageAction.CopyLink.labelWith(URL))
        assertEquals(R.string.message_action_copy_link, MessageAction.CopyLink.labelWith(null))
    }

    @Test
    fun `the menu groups the link's actions first, then the everyday ones, then the destructive ones`() {
        val actions =
            listOf(MessageAction.OpenLink, MessageAction.CopyPressedLink, MessageAction.Reply, MessageAction.Delete)

        assertEquals(
            listOf(
                listOf(MessageAction.OpenLink, MessageAction.CopyPressedLink),
                listOf(MessageAction.Reply),
                listOf(MessageAction.Delete),
            ),
            actionGroups(actions),
        )
    }

    private companion object {
        const val URL = "https://example.com/docs"
    }
}
