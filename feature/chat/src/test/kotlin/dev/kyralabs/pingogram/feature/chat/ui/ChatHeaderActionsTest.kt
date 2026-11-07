package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the experimental look's top bar and jump buttons get from [ChatViewModel] (`docs/DESIGN_SYSTEM.md` §13.5): the
 * unread mention count, the jump to the oldest unread mention, muting, and a group's people who wrote last and its
 * online members.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatHeaderActionsTest : ChatViewModelHarness() {
    private fun groupMessage(
        id: Long,
        from: Long,
    ) = message(id, chatId = GROUP_CHAT, sender = MessageSender.User(from))

    @Test
    fun `a group's header lists who wrote last, newest first and once each, with its online members`() =
        runViewModelTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
            val viewModel =
                showing(
                    groupChat.copy(onlineMemberCount = 36, unreadMentionCount = 2),
                    history(groupMessage(1, ADA), groupMessage(2, GRACE), groupMessage(3, ADA)),
                )
            runCurrent()

            val header = viewModel.state.header!!
            assertEquals(listOf(ADA, GRACE), header.members.map { it.userId })
            assertEquals(36, header.onlineCount)
            assertEquals(2, viewModel.state.unreadMentionCount)
        }

    @Test
    fun `jumping to a mention shows the oldest unread one in the list`() =
        runViewModelTest {
            chatSearch.mentionAnswer = { Outcome.Success(1) }
            val viewModel = showing(groupChat, history(groupMessage(1, ADA), groupMessage(2, GRACE)))
            runCurrent()

            send(viewModel, ChatEvent.JumpToMention)

            assertEquals(listOf(GROUP_CHAT to null), chatSearch.mentions)
            assertEquals(1L, (viewModel.anchor.target as AnchorTarget.Message).messageId)
        }

    @Test
    fun `no mention left to jump to says nothing, and a failure is a snackbar`() =
        runViewModelTest {
            val viewModel = showing(groupChat, history(groupMessage(1, ADA)))
            runCurrent()
            send(viewModel, ChatEvent.JumpToMention)

            chatSearch.mentionAnswer = { Outcome.Failure(DomainError.Network) }
            send(viewModel, ChatEvent.JumpToMention)

            assertEquals(ChatEffect.ShowError(DomainError.Network), viewModel.effects.first())
        }

    @Test
    fun `the mute toggle mutes and unmutes the chat`() =
        runViewModelTest {
            val viewModel = showing(groupChat, history(groupMessage(1, ADA)))
            runCurrent()

            send(viewModel, ChatEvent.SetMuted(true), ChatEvent.SetMuted(false))

            assertEquals(listOf(GROUP_CHAT to true, GROUP_CHAT to false), chats.muteCalls)
            assertTrue(viewModel.state.header?.isMuted == false)
        }

    @Test
    fun `a group's incoming bubbles take their sender's hue, role and mentions`() =
        runViewModelTest {
            users.setUsers(user(ADA).copy(accentColorId = 3), user(GRACE))
            groupMembers.administrators = {
                Outcome.Success(
                    FakeGroupInfoRepository.page(
                        GroupMember(MessageSender.User(ADA), MemberRole.Owner(isMember = true)),
                        GroupMember(MessageSender.User(GRACE), MemberRole.Member),
                    ),
                )
            }
            val mention =
                groupMessage(2, GRACE).copy(
                    content =
                        MessageContent.Text(
                            FormattedText("Hi Me", persistentListOf(TextEntity(3, 2, TextEntityType.MentionName(ME)))),
                            linkPreview = null,
                        ),
                )
            val viewModel = showing(groupChat, history(groupMessage(1, ADA), mention))
            runCurrent()

            val ada = viewModel.bubble(1)
            val grace = viewModel.bubble(2)
            assertEquals(3, ada.senderHue)
            assertEquals(SenderHues.indexOf(-1, GRACE), grace.senderHue)
            assertEquals(UiText.resource(R.string.search_sender_owner), ada.senderRole)
            assertEquals(null, grace.senderRole)
            assertTrue(grace.mentionsMe)
            assertTrue(!ada.mentionsMe)
            assertEquals(listOf(3 until 5), grace.myMentions)
            assertEquals(emptyList<IntRange>(), ada.myMentions)
        }

    @Test
    fun `a mention of one of the user's usernames is theirs, another's is not`() =
        runViewModelTest {
            users.setUsers(user(ME).copy(usernames = persistentListOf("Me_Myself")), user(GRACE))
            val text = "@me_myself and @someone"
            val mention =
                groupMessage(2, GRACE).copy(
                    containsUnreadMention = true,
                    content =
                        MessageContent.Text(
                            FormattedText(
                                text,
                                persistentListOf(
                                    TextEntity(0, 10, TextEntityType.Mention),
                                    TextEntity(15, 8, TextEntityType.Mention),
                                ),
                            ),
                            linkPreview = null,
                        ),
                )
            val viewModel = showing(groupChat, history(mention))
            runCurrent()

            assertEquals(listOf(0 until 10), viewModel.bubble(2).myMentions)
        }

    @Test
    fun `a channel's posts carry their views, and the unread divider counts the unread posts`() =
        runViewModelTest {
            val info =
                InteractionInfo(viewCount = 12_480, forwardCount = 0, replyCount = null, reactions = persistentListOf())
            val posts =
                (1L..4L).map { id ->
                    message(id, chatId = ChatFixtures.CHANNEL_CHAT, interactionInfo = info).copy(isChannelPost = true)
                }
            val viewModel =
                showing(
                    ChatFixtures.channelChat.copy(unreadCount = 2, lastReadInboxMessageId = 2),
                    history(*posts.toTypedArray()),
                )
            runCurrent()

            assertEquals(12_480, viewModel.bubble(4).views)
            assertTrue(viewModel.bubble(4).isChannelPost)
            val divider = viewModel.items.filterIsInstance<ChatItem.UnreadDivider>().single()
            assertEquals(2, divider.count)
            assertTrue(divider.isChannel)
        }
}
