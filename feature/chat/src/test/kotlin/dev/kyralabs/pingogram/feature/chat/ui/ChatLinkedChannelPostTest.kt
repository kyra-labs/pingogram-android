package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A channel's post in its discussion group, which Telegram forwards there as the channel itself ([ChatViewModel]'s
 * bubbles): the channel's name with the "Channel" label, and no "Forwarded from" line, unlike a post someone forwarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLinkedChannelPostTest : ChatViewModelHarness() {
    private fun fromChannel(messageId: Long) = ForwardInfo(MessageOrigin.Channel(CHANNEL_CHAT, messageId, ""), date = 0)

    @Test
    fun `a linked channel's post shows the channel with a label, not where it was forwarded from`() =
        runTest {
            chats.setChats(groupChat, channelChat)
            val post =
                message(1, chatId = GROUP_CHAT, sender = MessageSender.Chat(CHANNEL_CHAT), forwardInfo = fromChannel(9))
            messages.setHistory(GROUP_CHAT, history(post))

            val bubble = viewModel(GROUP_CHAT).bubble(1)

            assertEquals(UiText.Plain("Channel"), bubble.sender)
            assertNull(bubble.forwardedFrom)
            assertEquals(UiText.resource(R.string.chat_sender_channel), bubble.senderRole)
        }

    @Test
    fun `a post someone forwarded from the channel still says where it comes from`() =
        runTest {
            chats.setChats(groupChat, channelChat)
            messages.setHistory(GROUP_CHAT, history(message(2, chatId = GROUP_CHAT, forwardInfo = fromChannel(8))))

            val bubble = viewModel(GROUP_CHAT).bubble(2)

            assertEquals(UiText.resource(R.string.chat_forwarded_from, UiText.Plain("⁨Channel⁩")), bubble.forwardedFrom)
            assertNull(bubble.senderRole)
        }
}
