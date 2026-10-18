package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.draft
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.folder
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.supergroup
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The rows [ChatListViewModel] maps: previews, special chats, online dots, badges, statuses and times. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListRowsTest : ChatListViewModelHarness() {
    // Previews

    @Test
    fun `someone typing comes before the draft and the last message`() =
        runTest {
            actions.setActions(2, SenderAction(MessageSender.User(2), ChatAction.Typing))

            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2), draft = draft("plans")))

            assertEquals(ChatPreview.Action(res(UiR.string.action_typing)), row.preview)
        }

    @Test
    fun `a draft comes before the last message`() =
        runTest {
            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2), draft = draft("plans")))

            assertEquals(ChatPreview.Draft(UiText.Plain("plans")), row.preview)
        }

    @Test
    fun `the last message of a private chat shows without a sender`() =
        runTest {
            val row = rowOf(chat(id = 2, lastMessage = message(chatId = 2)))

            assertEquals(ChatPreview.Message(sender = null, text = UiText.Plain("Hello")), row.preview)
        }

    @Test
    fun `a chat without messages has no preview`() =
        runTest {
            assertEquals(ChatPreview.None, rowOf(chat(id = 2)).preview)
        }

    @Test
    fun `a group names who is typing`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            actions.setActions(GROUP, SenderAction(MessageSender.User(5), ChatAction.Typing))

            val row = rowOf(chat(id = GROUP, type = BASIC_GROUP))

            assertEquals(ChatPreview.Action(res(UiR.string.named_action_typing, UiText.Plain("Alice"))), row.preview)
        }

    @Test
    fun `a group names two people typing, and counts more`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"), user(6, firstName = "Bob"), user(7, firstName = "Carol"))
            actions.setActions(
                GROUP,
                SenderAction(MessageSender.User(5), ChatAction.Typing),
                SenderAction(MessageSender.User(6), ChatAction.Typing),
            )
            actions.setActions(
                OTHER_GROUP,
                SenderAction(MessageSender.User(5), ChatAction.Typing),
                SenderAction(MessageSender.User(6), ChatAction.Typing),
                SenderAction(MessageSender.User(7), ChatAction.Typing),
            )
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP),
                chat(id = OTHER_GROUP, type = ChatType.Supergroup(9, isChannel = false)),
            )

            val viewModel = viewModel()

            assertEquals(
                ChatPreview.Action(res(UiR.string.typing_two, UiText.Plain("Alice"), UiText.Plain("Bob"))),
                viewModel.item(GROUP).preview,
            )
            assertEquals(
                ChatPreview.Action(UiText.plural(UiR.plurals.typing_many, 3, 3)),
                viewModel.item(OTHER_GROUP).preview,
            )
        }

    @Test
    fun `people doing different things show the first of them`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"), user(6, firstName = "Bob"))
            actions.setActions(
                GROUP,
                SenderAction(MessageSender.User(6), ChatAction.RecordingVoiceNote),
                SenderAction(MessageSender.User(5), ChatAction.Typing),
            )

            val row = rowOf(chat(id = GROUP, type = BASIC_GROUP))

            assertEquals(
                ChatPreview.Action(res(UiR.string.named_action_recording_voice, UiText.Plain("Bob"))),
                row.preview,
            )
        }

    @Test
    fun `messages in groups show their sender's first name, or You`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice", lastName = "Smith"))
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP, lastMessage = message(GROUP, sender = MessageSender.User(5))),
                chat(
                    id = OTHER_GROUP,
                    type = ChatType.Supergroup(9, isChannel = false),
                    lastMessage = message(OTHER_GROUP, sender = MessageSender.User(ME), isOutgoing = true),
                ),
            )

            val viewModel = viewModel()

            assertEquals(UiText.Plain("Alice"), (viewModel.item(GROUP).preview as ChatPreview.Message).sender)
            assertEquals(
                res(UiR.string.preview_sender_you),
                (viewModel.item(OTHER_GROUP).preview as ChatPreview.Message).sender,
            )
        }

    @Test
    fun `channel posts, anonymous admins and service messages show no sender`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            val channel = ChatType.Supergroup(9, isChannel = true)
            chats.setChats(
                ChatListType.Main,
                chat(
                    id = 20,
                    type = channel,
                    lastMessage = message(20, sender = MessageSender.Chat(20), isChannelPost = true),
                ),
                chat(id = 21, type = BASIC_GROUP, lastMessage = message(21, sender = MessageSender.Chat(21))),
                chat(
                    id = 22,
                    type = BASIC_GROUP,
                    lastMessage =
                        message(
                            22,
                            sender = MessageSender.User(5),
                            content = MessageContent.Service(ServiceAction.MemberJoined),
                        ),
                ),
            )

            val viewModel = viewModel()

            assertNull((viewModel.item(20).preview as ChatPreview.Message).sender)
            assertNull((viewModel.item(21).preview as ChatPreview.Message).sender)
            assertEquals(
                ChatPreview.Message(sender = null, text = res(UiR.string.preview_joined, UiText.Plain("Alice"))),
                viewModel.item(22).preview,
            )
        }

    @Test
    fun `a changed sender name maps the row again`() =
        runTest {
            users.setUsers(user(5, firstName = "Alice"))
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = BASIC_GROUP, lastMessage = message(GROUP, sender = MessageSender.User(5))),
            )
            val viewModel = viewModel()

            users.setUsers(user(5, firstName = "Alicia"))
            runCurrent()

            assertEquals(UiText.Plain("Alicia"), (viewModel.item(GROUP).preview as ChatPreview.Message).sender)
        }

    // Special chats

    @Test
    fun `Saved Messages has its own name and icon, and nobody online or typing`() =
        runTest {
            users.setUsers(user(ME, status = UserStatus.Online(NOW.epochSecond + 300)))
            actions.setActions(ME, SenderAction(MessageSender.User(ME), ChatAction.Typing))

            val lastMessage = message(ME, sender = MessageSender.User(ME), isOutgoing = true)

            val row = rowOf(chat(id = ME, title = "Me", lastMessage = lastMessage))

            assertEquals(res(UiR.string.chat_saved_messages), row.title)
            assertEquals(UiR.drawable.ic_bookmark_filled, row.placeholderIcon)
            assertFalse(row.isOnline)
            assertNull(row.outgoingStatus)
            assertTrue(row.preview is ChatPreview.Message)
        }

    @Test
    fun `a deleted account has its own name and a person icon`() =
        runTest {
            users.setUsers(user(2, firstName = "", type = UserType.Deleted))

            val row = rowOf(chat(id = 2, title = ""))

            assertEquals(res(UiR.string.user_deleted_account), row.title)
            assertEquals(UiR.drawable.ic_person_filled, row.placeholderIcon)
        }

    @Test
    fun `other chats keep their title and no icon`() =
        runTest {
            val row = rowOf(chat(id = 2, title = "Ada"))

            assertEquals(UiText.Plain("Ada"), row.title)
            assertNull(row.placeholderIcon)
        }

    // Online

    @Test
    fun `a user is online until the status expires, checked every minute`() =
        runTest {
            users.setUsers(user(2, status = UserStatus.Online(expiresAtEpochSeconds = NOW.epochSecond + 60)))
            chats.setChats(ChatListType.Main, chat(id = 2))
            val viewModel = viewModel()
            assertTrue(viewModel.item(2).isOnline)

            clock.setTime(Instant.parse("2026-10-18T09:42:00Z"))
            runCurrent()
            assertTrue(viewModel.item(2).isOnline)

            clock.setTime(Instant.parse("2026-10-18T09:43:00Z"))
            runCurrent()
            assertFalse(viewModel.item(2).isOnline)
        }

    @Test
    fun `bots and offline users are not online`() =
        runTest {
            val online = UserStatus.Online(NOW.epochSecond + 300)
            users.setUsers(
                user(2, status = online, type = UserType.Bot),
                user(3, status = UserStatus.Offline(NOW.epochSecond - 60)),
            )
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))

            val viewModel = viewModel()

            assertFalse(viewModel.item(2).isOnline)
            assertFalse(viewModel.item(3).isOnline)
        }

    // Badges and flags

    @Test
    fun `badges come from the user or the supergroup, warnings first`() =
        runTest {
            users.setUsers(user(2, isVerified = true), user(3, isFake = true))
            groups.supergroupMap.value =
                persistentMapOf(
                    9L to supergroup(9, isVerified = true, isScam = true),
                    10L to supergroup(10, isChannel = true, isVerified = true),
                )
            chats.setChats(
                ChatListType.Main,
                chat(id = 2),
                chat(id = 3),
                chat(id = 20, type = ChatType.Supergroup(9, isChannel = false)),
                chat(id = 21, type = ChatType.Supergroup(10, isChannel = true)),
                chat(id = 22, type = BASIC_GROUP),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf(TitleBadge.Verified, TitleBadge.Fake, TitleBadge.Scam, TitleBadge.Verified, null),
                viewModel.items.map { it.badge },
            )
        }

    @Test
    fun `a secret chat shows its user and a lock`() =
        runTest {
            users.setUsers(user(2, isVerified = true, status = UserStatus.Online(NOW.epochSecond + 300)))

            val row = rowOf(chat(id = 30, type = ChatType.Secret(secretChatId = 1, userId = 2)))

            assertTrue(row.isSecret)
            assertTrue(row.isOnline)
            assertEquals(TitleBadge.Verified, row.badge)
        }

    @Test
    fun `unread counts, mentions, reactions, the unread mark, mute and pins are shown`() =
        runTest {
            val row =
                rowOf(
                    chat(
                        id = 2,
                        unreadCount = 3,
                        unreadMentionCount = 1,
                        unreadReactionCount = 2,
                        isMarkedAsUnread = true,
                        isMuted = true,
                        pinnedIn = listOf(ChatListType.Main),
                    ),
                )

            assertEquals(3, row.unreadCount)
            assertTrue(row.hasUnreadMention)
            assertTrue(row.hasUnreadReactions)
            assertTrue(row.isMarkedAsUnread)
            assertTrue(row.isMuted)
            assertTrue(row.isPinned)
        }

    @Test
    fun `a chat is pinned only in the lists it is pinned in`() =
        runTest {
            chats.folderList.value = persistentListOf(folder(1))
            val pinnedInFolder =
                chat(
                    id = 2,
                    lists = listOf(ChatListType.Main, ChatListType.Folder(1)),
                    pinnedIn = listOf(ChatListType.Folder(1)),
                )
            chats.setChats(ChatListType.Main, pinnedInFolder)
            chats.setChats(ChatListType.Folder(1), pinnedInFolder)
            val viewModel = viewModel()
            assertFalse(viewModel.item(2).isPinned)

            send(viewModel, ChatListEvent.SelectFolder(1))

            assertTrue(viewModel.item(2).isPinned)
        }

    // Outgoing messages

    @Test
    fun `the current user's last message shows whether it is pending, failed, sent or read`() =
        runTest {
            val failed = SendingState.Failed(DomainError.Network, canRetry = true, retryAfter = null)
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, lastMessage = outgoing(2, sendingState = SendingState.Pending)),
                chat(id = 3, lastMessage = outgoing(3, sendingState = failed)),
                chat(id = 4, lastMessage = outgoing(4), lastReadOutboxMessageId = 9),
                chat(id = 5, lastMessage = outgoing(5), lastReadOutboxMessageId = 10),
                chat(id = 6, lastMessage = message(6)),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf(OutgoingStatus.Pending, OutgoingStatus.Failed, OutgoingStatus.Sent, OutgoingStatus.Read, null),
                viewModel.items.map { it.outgoingStatus },
            )
        }

    @Test
    fun `channel posts show no status`() =
        runTest {
            val channel = ChatType.Supergroup(9, isChannel = true)

            val row = rowOf(chat(id = 20, type = channel, lastMessage = outgoing(20, sender = MessageSender.Chat(20))))

            assertNull(row.outgoingStatus)
        }

    // Times

    @Test
    fun `times show the time today, the weekday this week, the day this year, else the date`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                chat(id = 2, lastMessage = message(2, date = Instant.parse("2026-10-18T08:05:00Z"))),
                chat(id = 3, lastMessage = message(3, date = Instant.parse("2026-10-17T23:00:00Z"))),
                chat(id = 4, lastMessage = message(4, date = Instant.parse("2026-10-12T10:00:00Z"))),
                chat(id = 5, lastMessage = message(5, date = Instant.parse("2026-10-11T10:00:00Z"))),
                chat(id = 6, lastMessage = message(6, date = Instant.parse("2025-03-17T10:00:00Z"))),
                chat(id = 7),
            )

            val viewModel = viewModel()

            assertEquals(
                listOf("8:05 AM", "Sat", "Mon", "Oct 11", "3/17/2025", ""),
                viewModel.items.map { it.time.replace(' ', ' ') },
            )
        }

    @Test
    fun `times follow the 24-hour setting`() =
        runTest {
            timePatterns.uses24HourClock = true

            val row = rowOf(chat(id = 2, lastMessage = message(2, date = Instant.parse("2026-10-18T08:05:00Z"))))

            assertEquals("08:05", row.time)
        }

    @Test
    fun `a changed clock setting shows at the next minute`() =
        runTest {
            val lastMessage = message(2, date = Instant.parse("2026-10-18T20:05:00Z"))
            chats.setChats(ChatListType.Main, chat(id = 2, lastMessage = lastMessage))
            val viewModel = viewModel()
            assertEquals("8:05 PM", viewModel.item(2).time.replace('\u202F', ' '))

            timePatterns.uses24HourClock = true
            clock.setTime(Instant.parse("2026-10-18T09:42:00Z"))
            runCurrent()

            assertEquals("20:05", viewModel.item(2).time)
        }

    @Test
    fun `a newer draft shows its own time and hides the message status`() =
        runTest {
            val row =
                rowOf(
                    chat(
                        id = 2,
                        lastMessage = outgoing(2, date = Instant.parse("2026-10-12T10:00:00Z")),
                        draft = draft("plans", date = Instant.parse("2026-10-18T08:05:00Z")),
                    ),
                )

            assertEquals("8:05 AM", row.time.replace(' ', ' '))
            assertNull(row.outgoingStatus)
        }

    // Performance

    @Test
    fun `rows whose chat did not change are reused when another user changes`() =
        runTest {
            users.setUsers(user(2), user(3))
            chats.setChats(ChatListType.Main, chat(id = 2), chat(id = 3))
            val viewModel = viewModel()
            val before = viewModel.items

            users.setUsers(user(2), user(3, status = UserStatus.Online(NOW.epochSecond + 300)))
            runCurrent()

            assertSame(before[0], viewModel.items[0])
            assertNotSame(before[1], viewModel.items[1])
            assertTrue(viewModel.items[1].isOnline)
        }
}
