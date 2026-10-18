package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.LinkPreview
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.service
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The items of [ChatViewModel]: grouping, dates, the unread divider, and what each bubble shows. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatItemsTest : ChatViewModelHarness() {
    // Grouping

    @Test
    fun `messages of one sender a few minutes apart form a group`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = minutesAgo(10)),
                        message(2, date = minutesAgo(8)),
                        message(3, date = minutesAgo(5)),
                    ),
                )

            assertEquals(
                listOf(GroupPosition.Last, GroupPosition.Middle, GroupPosition.First),
                viewModel.bubbles.map { it.position },
            )
        }

    @Test
    fun `a longer gap starts a new group`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = minutesAgo(20)),
                        message(
                            2,
                            date =
                                minutesAgo(20 - GAP_MINUTES - 1),
                        ),
                    ),
                )

            assertEquals(listOf(GroupPosition.Single, GroupPosition.Single), viewModel.bubbles.map { it.position })
        }

    @Test
    fun `another sender starts a new group`() =
        runTest {
            val viewModel =
                showing(chat(), history(message(1, date = minutesAgo(2)), outgoing(2, date = minutesAgo(1))))

            assertEquals(listOf(GroupPosition.Single, GroupPosition.Single), viewModel.bubbles.map { it.position })
        }

    @Test
    fun `a new day starts a new group`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = Instant.parse("2026-10-17T23:59:00Z")),
                        message(2, date = Instant.parse("2026-10-18T00:01:00Z")),
                    ),
                )

            assertEquals(listOf(GroupPosition.Single, GroupPosition.Single), viewModel.bubbles.map { it.position })
        }

    @Test
    fun `a service message breaks a group and shows as a pill`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"))
            val viewModel =
                showing(
                    groupChat,
                    history(
                        message(1, chatId = GROUP_CHAT, date = minutesAgo(3)),
                        service(2, ServiceAction.MemberJoined, date = minutesAgo(2)),
                        message(3, chatId = GROUP_CHAT, date = minutesAgo(1)),
                    ),
                )

            assertEquals(listOf("m3", "s2", "m1", "date:2026-10-18"), viewModel.outline())
            assertEquals(listOf(GroupPosition.Single, GroupPosition.Single), viewModel.bubbles.map { it.position })
            val service = viewModel.items.filterIsInstance<ChatItem.Service>().single()
            assertEquals(res(UiR.string.preview_joined, UiText.Plain("Ada")), service.text)
        }

    @Test
    fun `a service message names the members it concerns`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
            val viewModel = showing(groupChat, history(service(1, ServiceAction.MemberRemoved(GRACE))))

            val service = viewModel.items.filterIsInstance<ChatItem.Service>().single()
            assertEquals(
                res(UiR.string.preview_removed_member, UiText.Plain("Ada"), UiText.Plain("Grace")),
                service.text,
            )
        }

    // Dates

    @Test
    fun `a date separator goes above the first message of each day`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, date = Instant.parse("2025-12-31T12:00:00Z")),
                        message(2, date = Instant.parse("2026-03-17T12:00:00Z")),
                        message(3, date = Instant.parse("2026-10-17T12:00:00Z")),
                        message(4, date = Instant.parse("2026-10-18T08:00:00Z")),
                        message(5, date = Instant.parse("2026-10-18T09:00:00Z")),
                    ),
                )

            assertEquals(
                listOf(
                    "m5",
                    "m4",
                    "date:2026-10-18",
                    "m3",
                    "date:2026-10-17",
                    "m2",
                    "date:2026-03-17",
                    "m1",
                    "date:2025-12-31",
                ),
                viewModel.outline(),
            )
            assertEquals(
                listOf(
                    res(R.string.chat_date_today),
                    res(R.string.chat_date_yesterday),
                    UiText.Plain("Tuesday, March 17"),
                    UiText.Plain("December 31, 2025"),
                ),
                viewModel.items.filterIsInstance<ChatItem.DateSeparator>().map { it.date },
            )
        }

    @Test
    fun `every item knows its day, for the floating date pill`() =
        runTest {
            val viewModel =
                showing(chat(), history(message(1, date = Instant.parse("2026-10-17T12:00:00Z")), message(2)))

            assertEquals(
                listOf(res(R.string.chat_date_today), res(R.string.chat_date_today)) +
                    listOf(res(R.string.chat_date_yesterday), res(R.string.chat_date_yesterday)),
                viewModel.items.map { it.date },
            )
        }

    @Test
    fun `times are formatted for the list`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, date = Instant.parse("2026-10-18T07:05:00Z"))))

            assertEquals("7:05 AM", viewModel.bubble(1).time)
        }

    // The unread divider

    @Test
    fun `the unread divider goes above the first unread incoming message`() =
        runTest {
            val viewModel =
                showing(
                    chat(unreadCount = 2, lastReadInboxMessageId = 2),
                    history(message(1), message(2), outgoing(3), message(4), message(5)),
                )

            assertEquals(listOf("m5", "m4", "unread", "m3", "m2", "m1", "date:2026-10-18"), viewModel.outline())
        }

    @Test
    fun `the unread divider stays where it was as messages arrive and are read`() =
        runTest {
            val chat = chat(unreadCount = 1, lastReadInboxMessageId = 1)
            val viewModel = showing(chat, history(message(1), message(2)))

            messages.setHistory(PRIVATE_CHAT, history(message(1), message(2), message(3), message(4)))
            chats.setChats(chat.copy(unreadCount = 0, lastReadInboxMessageId = 4))
            runCurrent()

            assertEquals(listOf("m4", "m3", "m2", "unread", "m1", "date:2026-10-18"), viewModel.outline())
        }

    @Test
    fun `nothing unread when the chat was opened shows no divider, also for new messages`() =
        runTest {
            val viewModel = showing(chat(lastReadInboxMessageId = 2), history(message(1), message(2)))

            messages.setHistory(PRIVATE_CHAT, history(message(1), message(2), message(3)))
            runCurrent()

            assertFalse(viewModel.items.any { it is ChatItem.UnreadDivider })
        }

    @Test
    fun `the unread divider survives process death`() =
        runTest {
            chats.setChats(chat(unreadCount = 0, lastReadInboxMessageId = 3))
            messages.setHistory(PRIVATE_CHAT, history(message(1), message(2), message(3)))

            val viewModel = viewModel(handle = SavedStateHandle(mapOf(ChatViewModel.KEY_UNREAD_AFTER to 1L)))

            assertEquals(listOf("m3", "m2", "unread", "m1", "date:2026-10-18"), viewModel.outline())
        }

    @Test
    fun `the unread divider ends a group`() =
        runTest {
            val viewModel =
                showing(
                    chat(unreadCount = 1, lastReadInboxMessageId = 1),
                    history(message(1, date = minutesAgo(2)), message(2, date = minutesAgo(1))),
                )

            assertEquals(listOf(GroupPosition.Single, GroupPosition.Single), viewModel.bubbles.map { it.position })
        }

    // Senders

    @Test
    fun `group messages show the sender's name on the first bubble and the avatar on the last`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
            val viewModel =
                showing(
                    groupChat,
                    history(
                        message(1, chatId = GROUP_CHAT, date = minutesAgo(3)),
                        message(2, chatId = GROUP_CHAT, date = minutesAgo(2)),
                        message(3, chatId = GROUP_CHAT, date = minutesAgo(1)),
                    ),
                )

            val (newest, middle, oldest) = viewModel.bubbles
            assertEquals(listOf(false, false, true), listOf(newest, middle, oldest).map { it.showsSenderName })
            assertEquals(listOf(true, false, false), listOf(newest, middle, oldest).map { it.avatar != null })
            assertTrue(listOf(newest, middle, oldest).all { it.hasAvatarColumn })
            assertEquals(UiText.Plain("Ada Lovelace"), oldest.sender)
            assertEquals(SenderAvatar(ADA, UiText.Plain("Ada Lovelace"), null, null), newest.avatar)
        }

    @Test
    fun `private chats and outgoing messages show no names or avatars`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"))
            val private = showing(chat(), history(message(1), outgoing(2)))
            assertTrue(private.bubbles.none { it.showsSenderName || it.avatar != null || it.hasAvatarColumn })

            val group = showing(groupChat, history(outgoing(3, chatId = GROUP_CHAT)))

            assertTrue(group.bubbles.none { it.showsSenderName || it.avatar != null || it.hasAvatarColumn })
            assertEquals(res(UiR.string.preview_sender_you), group.bubble(3).sender)
        }

    @Test
    fun `a group's anonymous admins post as the group`() =
        runTest {
            val viewModel =
                showing(groupChat, history(message(1, chatId = GROUP_CHAT, sender = MessageSender.Chat(GROUP_CHAT))))

            val bubble = viewModel.bubble(1)
            assertEquals(UiText.Plain("Group"), bubble.sender)
            assertEquals(GROUP_CHAT, bubble.senderId)
        }

    @Test
    fun `channel posts show no names`() =
        runTest {
            val post = message(1, chatId = channelChat.id, sender = MessageSender.Chat(channelChat.id))

            val viewModel = showing(channelChat, history(post))

            assertFalse(viewModel.bubble(1).showsSenderName)
            assertNull(viewModel.bubble(1).avatar)
        }

    // Outgoing states

    @Test
    fun `outgoing messages are pending, failed, sent or read`() =
        runTest {
            val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
            val viewModel =
                showing(
                    chat(lastReadOutboxMessageId = 2),
                    history(
                        outgoing(1),
                        outgoing(2),
                        outgoing(3),
                        outgoing(4, sendingState = failed),
                        outgoing(5, sendingState = SendingState.Pending),
                        message(6),
                    ),
                )

            assertEquals(
                listOf(
                    null,
                    OutgoingStatus.Pending,
                    OutgoingStatus.Failed,
                    OutgoingStatus.Sent,
                    OutgoingStatus.Read,
                    OutgoingStatus.Read,
                ),
                viewModel.bubbles.map { it.status },
            )
        }

    @Test
    fun `messages read later turn read`() =
        runTest {
            val viewModel = showing(chat(), history(outgoing(1)))

            chats.setChats(chat(lastReadOutboxMessageId = 1))
            runCurrent()

            assertEquals(OutgoingStatus.Read, viewModel.bubble(1).status)
        }

    @Test
    fun `channels and Saved Messages show no states`() =
        runTest {
            val channel = showing(channelChat, history(outgoing(1, chatId = channelChat.id)))
            assertNull(channel.bubble(1).status)

            val saved = showing(chat(id = ME), history(outgoing(2, chatId = ME)))

            assertNull(saved.bubble(2).status)
        }

    // Forwards

    @Test
    fun `a forwarded message names where it comes from`() =
        runTest {
            users.setUsers(user(GRACE, firstName = "Grace", lastName = "Hopper"))
            chats.setChats(chat(), chat(id = -300, title = "News"))
            messages.setHistory(
                PRIVATE_CHAT,
                history(
                    message(1, forwardInfo = ForwardInfo(MessageOrigin.User(GRACE), date = 0)),
                    message(2, forwardInfo = ForwardInfo(MessageOrigin.HiddenUser("Anonymous"), date = 0)),
                    message(
                        3,
                        forwardInfo =
                            ForwardInfo(
                                MessageOrigin.Channel(-300, messageId = 9, authorSignature = "Ed"),
                                date = 0,
                            ),
                    ),
                    message(
                        4,
                        forwardInfo =
                            ForwardInfo(
                                MessageOrigin.Channel(-300, messageId = 9, authorSignature = ""),
                                date = 0,
                            ),
                    ),
                ),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf(
                    UiText.Plain("Grace Hopper"),
                    UiText.Plain("Anonymous"),
                    res(R.string.chat_forwarded_name_signed, UiText.Plain("News"), "Ed"),
                    UiText.Plain("News"),
                ).map { res(R.string.chat_forwarded_from, it) },
                listOf(1L, 2L, 3L, 4L).map { viewModel.bubble(it).forwardedFrom },
            )
        }

    // Replies

    @Test
    fun `a reply quotes the replied message from the history`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, content = MessageContent.Text(text("The original"), null)),
                        outgoing(2).copy(replyTo = replyTo(1)),
                    ),
                )

            assertEquals(
                ReplyQuote(UiText.Plain("Ada Lovelace"), ADA, UiText.Plain("The original")),
                viewModel.bubble(2).reply,
            )
            assertTrue(messages.repliedRequests.isEmpty())
        }

    @Test
    fun `a reply with a quote shows the quoted part`() =
        runTest {
            val quoted = replyTo(1).copy(quote = text("Original"))
            val viewModel = showing(chat(), history(message(1), outgoing(2).copy(replyTo = quoted)))

            assertEquals(UiText.Plain("Original"), viewModel.bubble(2).reply?.text)
        }

    @Test
    fun `a reply to the user's own message names them`() =
        runTest {
            val viewModel = showing(chat(), history(outgoing(1), message(2, replyTo = replyTo(1))))

            assertEquals(res(UiR.string.preview_sender_you), viewModel.bubble(2).reply?.sender)
        }

    @Test
    fun `a replied message older than the history is fetched once, loading meanwhile`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"))
            val answer = CompletableDeferred<Outcome<dev.kyralabs.pingogram.core.domain.model.Message>>()
            messages.onRepliedMessage = { _, _ -> answer.await() }
            val viewModel = showing(chat(), history(outgoing(5).copy(replyTo = replyTo(1)), reachedStart = false))
            assertEquals(ReplyQuote(null, null, res(R.string.chat_reply_loading)), viewModel.bubble(5).reply)

            answer.complete(Outcome.Success(message(1, content = MessageContent.Text(text("Long ago"), null))))
            runCurrent()
            messages.setHistory(
                PRIVATE_CHAT,
                history(outgoing(5).copy(replyTo = replyTo(1)), outgoing(6), reachedStart = false),
            )
            runCurrent()

            assertEquals(ReplyQuote(UiText.Plain("Ada"), ADA, UiText.Plain("Long ago")), viewModel.bubble(5).reply)
            assertEquals(listOf(PRIVATE_CHAT to 5L), messages.repliedRequests)
        }

    @Test
    fun `a deleted replied message shows as deleted, other failures as unavailable`() =
        runTest {
            messages.onRepliedMessage = { _, replying ->
                Outcome.Failure(if (replying == 5L) DomainError.NotFound else DomainError.Network)
            }

            val viewModel =
                showing(
                    chat(),
                    history(message(5, replyTo = replyTo(1)), message(6, replyTo = replyTo(2)), reachedStart = false),
                )

            assertEquals(ReplyQuote(null, null, res(R.string.chat_reply_deleted)), viewModel.bubble(5).reply)
            assertEquals(ReplyQuote(null, null, res(R.string.chat_reply_unavailable)), viewModel.bubble(6).reply)
        }

    @Test
    fun `a reply to another chat shows what TDLib bundled while the message is not there`() =
        runTest {
            chats.setChats(chat(), chat(id = -300, title = "News"))
            messages.setHistory(
                PRIVATE_CHAT,
                history(
                    message(5, replyTo = ReplyTo.Message(-300, 9, null, MessageOrigin.Channel(-300, 9, ""), 0, null)),
                ),
            )
            messages.onRepliedMessage = { _, _ -> Outcome.Failure(DomainError.Network) }

            val viewModel = viewModel()

            assertEquals(
                ReplyQuote(UiText.Plain("News"), -300, res(R.string.chat_reply_message)),
                viewModel.bubble(5).reply,
            )
        }

    @Test
    fun `a reply to a story quotes the story`() =
        runTest {
            val viewModel =
                showing(chat(), history(message(5, replyTo = ReplyTo.Story(storyPosterChatId = ADA, storyId = 1))))

            assertEquals(ReplyQuote(null, null, res(UiR.string.preview_story)), viewModel.bubble(5).reply)
        }

    // What a bubble shows

    @Test
    fun `reactions show their emoji and counts, chosen ones marked`() =
        runTest {
            val reactions =
                listOf(
                    Reaction(ReactionType.Emoji("👍"), totalCount = 3, isChosen = true),
                    Reaction(ReactionType.CustomEmoji(customEmojiId = 7), totalCount = 1, isChosen = false),
                    Reaction(ReactionType.Paid, totalCount = 12, isChosen = false),
                ).toImmutableList()
            val info = InteractionInfo(viewCount = 0, forwardCount = 0, replyCount = null, reactions = reactions)

            val viewModel = showing(chat(), history(message(1, interactionInfo = info)))

            assertEquals(
                listOf(
                    ReactionChip(ReactionLabel.Emoji("👍"), 3, isChosen = true),
                    ReactionChip(ReactionLabel.CustomEmoji, 1, isChosen = false),
                    ReactionChip(ReactionLabel.Paid, 12, isChosen = false),
                ),
                viewModel.bubble(1).reactions,
            )
        }

    @Test
    fun `a link preview shows its site, title and description`() =
        runTest {
            val preview =
                LinkPreview(
                    url = "https://example.org",
                    displayUrl = "example.org",
                    siteName = "Example",
                    title = "An example",
                    description = text("A page"),
                    photo = null,
                    showLargeMedia = false,
                    showAboveText = false,
                )

            val viewModel =
                showing(chat(), history(message(1, content = MessageContent.Text(text("example.org"), preview))))

            assertEquals(LinkPreviewCard("Example", "An example", "A page"), viewModel.bubble(1).linkPreview)
        }

    @Test
    fun `media shows its kind for now, with the caption as text`() =
        runTest {
            val caption = FormattedText("Sunset", persistentListOf(TextEntity(0, 6, TextEntityType.Bold)))
            val photo = MessageContent.Photo(Photo(persistentListOf(), null), caption, false, false, false)

            val bubble = showing(chat(), history(message(1, content = photo))).bubble(1)

            assertEquals(res(UiR.string.preview_photo), bubble.contentLabel)
            assertSame(caption, bubble.text)
            assertSame(photo, bubble.content)
        }

    @Test
    fun `text messages have no kind label`() =
        runTest {
            val bubble = showing(chat(), history(message(1))).bubble(1)

            assertNull(bubble.contentLabel)
            assertEquals("Message 1", bubble.text.text)
        }

    @Test
    fun `edited messages say so`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, editDate = NOW.epochSecond), message(2)))

            assertEquals(listOf(false, true), viewModel.bubbles.map { it.isEdited })
        }

    @Test
    fun `tapping a spoiler reveals the spoilers of that message`() =
        runTest {
            val viewModel = showing(chat(), history(message(1), message(2)))

            send(viewModel, ChatEvent.RevealSpoilers(2))

            assertEquals(listOf(true, false), viewModel.bubbles.map { it.spoilersRevealed })
        }

    @Test
    fun `items that did not change are reused when something else changes`() =
        runTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE))
            val viewModel = showing(chat(), history(message(1), message(2)))
            val before = viewModel.bubble(1)

            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, status = UserStatus.Recently))
            messages.setHistory(PRIVATE_CHAT, history(message(1), message(2), message(3, date = NOW.plusSeconds(1))))
            runCurrent()

            assertSame(before, viewModel.bubble(1))
        }

    private fun minutesAgo(minutes: Long): Instant = NOW.minus(Duration.ofMinutes(minutes))

    private companion object {
        val GAP_MINUTES = GROUP_GAP_SECONDS / 60
    }
}
