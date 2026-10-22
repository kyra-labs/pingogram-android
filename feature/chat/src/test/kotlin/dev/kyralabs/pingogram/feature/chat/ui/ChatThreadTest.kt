package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageThread
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * Comment threads and replies in [ChatViewModel] (`REQUIREMENTS.md` FR-C9): the link under a channel post or a replied
 * group message, opening its thread, and the thread's conversation (its head, title, unread state, what is sent, who
 * may write, and a thread that cannot be fetched).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatThreadTest : ChatViewModelHarness() {
    private val discussion =
        chat(id = DISCUSSION, type = ChatType.Supergroup(SUPERGROUP_ID, isChannel = false), title = "Channel chat")

    private fun replies(count: Int?) = InteractionInfo(0, 0, replyCount = count, reactions = persistentListOf())

    /** The channel's post, as the discussion group has it: forwarded from the channel, with its comments. */
    private val head =
        message(
            THREAD,
            chatId = DISCUSSION,
            forwardInfo = ForwardInfo(MessageOrigin.Channel(CHANNEL_CHAT, POST, ""), date = 0),
            interactionInfo = replies(12),
        )

    private val thread =
        MessageThread(
            chatId = DISCUSSION,
            threadId = THREAD,
            head = persistentListOf(head),
            replyCount = 12,
            unreadCount = 2,
            lastReadInboxMessageId = THREAD + 1,
            lastReadOutboxMessageId = 0,
        )

    /** The channel with post [POST] of [count] comments (null: comments are off). */
    private fun TestScope.inChannel(count: Int?): ChatViewModel {
        chats.setChats(channelChat)
        groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(10))
        messages.setHistory(
            CHANNEL_CHAT,
            history(message(POST, chatId = CHANNEL_CHAT, interactionInfo = replies(count))),
        )
        return viewModel(CHANNEL_CHAT)
    }

    /** The conversation of the post's comments, its replies [THREAD] + 1 to + 3, the user [status] in the group. */
    private fun TestScope.inThread(
        status: ChatMemberStatus = ChatMemberStatus.Member,
        joinToSendMessages: Boolean = false,
    ): ChatViewModel {
        chats.setChats(discussion)
        groups.supergroupMap.value =
            mapOf(
                SUPERGROUP_ID to
                    supergroup(10, isChannel = false, status).copy(joinToSendMessages = joinToSendMessages),
            )
        threads.threads[DISCUSSION to THREAD] = thread
        val replies: List<Message> = (1L..3L).map { message(THREAD + it, chatId = DISCUSSION) }
        messages.setHistory(DISCUSSION, history(*replies.toTypedArray()))
        return viewModel(DISCUSSION, topic = MessageTopic.Thread(THREAD))
    }

    @Test
    fun `a channel post links to its comments, even before the first`() =
        runViewModelTest {
            assertEquals(ThreadLink(POST, 12, isComments = true), inChannel(count = 12).bubble(POST).thread)
            assertEquals(ThreadLink(POST, 0, isComments = true), inChannel(count = 0).bubble(POST).thread)
            assertNull(inChannel(count = null).bubble(POST).thread)
        }

    @Test
    fun `a group message links to its replies once there are any`() =
        runViewModelTest {
            chats.setChats(groupChat)
            messages.setHistory(
                GROUP_CHAT,
                history(
                    message(1, chatId = GROUP_CHAT, interactionInfo = replies(3)),
                    message(2, chatId = GROUP_CHAT, interactionInfo = replies(0)),
                ),
            )
            val viewModel = viewModel(GROUP_CHAT)

            assertEquals(ThreadLink(1, 3, isComments = false), viewModel.bubble(1).thread)
            assertNull(viewModel.bubble(2).thread)
        }

    @Test
    fun `the link opens the thread in the discussion group, or says why it cannot`() =
        runViewModelTest {
            val viewModel = inChannel(count = 12)
            threads.threads[CHANNEL_CHAT to POST] = thread

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenThread(POST))
                assertEquals(ChatEffect.OpenThread(DISCUSSION, MessageTopic.Thread(THREAD)), awaitItem())

                threads.threads.clear()
                send(viewModel, ChatEvent.OpenThread(POST))
                assertEquals(ChatEffect.ShowError(DomainError.NotFound), awaitItem())
            }
        }

    @Test
    fun `the thread starts with the post, without its link, its count under the chat's name`() =
        runViewModelTest {
            val viewModel = inThread()

            assertEquals(listOf(THREAD + 3, THREAD + 2, THREAD + 1, THREAD), viewModel.bubbles.map { it.id })
            assertNull(viewModel.bubble(THREAD).thread)
            assertTrue(messages.topics.isNotEmpty() && messages.topics.all { it == MessageTopic.Thread(THREAD) })
            assertEquals(UiText.Plain("Channel chat"), viewModel.state.header?.title)
            assertEquals(ChatSubtitle.Status(UiText.plural(ChatR.plurals.thread_comments, 12, 12)), viewModel.subtitle)
            assertEquals(2, viewModel.state.unreadCount)
        }

    @Test
    fun `a comment goes into the thread`() =
        runViewModelTest {
            val viewModel = inThread()
            viewModel.composerText.state.edit { replace(0, length, "Nice post") }
            Snapshot.sendApplyNotifications()
            runCurrent()

            send(viewModel, ChatEvent.Send(SendOptions()))

            assertEquals(SendOptions(topic = MessageTopic.Thread(THREAD)), messages.sent.single().third)
        }

    @Test
    fun `anyone may comment unless the group asks to join first`() =
        runViewModelTest {
            val composer = inThread(status = ChatMemberStatus.Left).state.composer
            assertEquals(false, (composer as ComposerUiState.Available).canSchedule)
            assertInstanceOf(
                ComposerUiState.Unavailable::class.java,
                inThread(status = ChatMemberStatus.Left, joinToSendMessages = true).state.composer,
            )
        }

    @Test
    fun `a thread that cannot be fetched shows the error, and Try again asks again`() =
        runViewModelTest {
            chats.setChats(discussion)
            messages.setHistory(DISCUSSION, history(message(THREAD + 1, chatId = DISCUSSION)))
            threads.failure = DomainError.Network
            val viewModel = viewModel(DISCUSSION, topic = MessageTopic.Thread(THREAD))
            assertEquals(ChatContent.Error(DomainError.Network), viewModel.state.content)

            threads.threads[DISCUSSION to THREAD] = thread
            send(viewModel, ChatEvent.Retry)

            assertEquals(2, threads.requests.size)
            assertInstanceOf(ChatContent.Content::class.java, viewModel.state.content)
        }

    private companion object {
        const val DISCUSSION = -1_000_000_000_400L
        const val POST = 50L
        const val THREAD = 900L
    }
}
