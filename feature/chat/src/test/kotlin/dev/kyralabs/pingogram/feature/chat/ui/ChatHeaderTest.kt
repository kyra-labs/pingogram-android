package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The header of [ChatViewModel]: title, avatar and every kind of subtitle. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatHeaderTest : ChatViewModelHarness() {
    @Test
    fun `shows the chat's title and photo`() =
        runTest {
            val viewModel = privateChat(UserStatus.Recently)

            val header = viewModel.state.header!!
            assertEquals(UiText.Plain("Chat $PRIVATE_CHAT"), header.title)
            assertEquals(PRIVATE_CHAT, header.chatId)
            assertNull(header.placeholderIcon)
        }

    @Test
    fun `a user online now is online`() =
        runTest {
            val viewModel = privateChat(UserStatus.Online(expiresAtEpochSeconds = NOW.epochSecond + 60))

            assertEquals(status(res(R.string.chat_subtitle_online)), viewModel.subtitle)
        }

    @Test
    fun `an online status that expired counts as last seen when it expired`() =
        runTest {
            val viewModel = privateChat(UserStatus.Online(expiresAtEpochSeconds = NOW.minusSeconds(10).epochSecond))

            assertEquals(status(res(R.string.chat_subtitle_last_seen_just_now)), viewModel.subtitle)
        }

    @Test
    fun `last seen within a minute is just now`() =
        runTest {
            val viewModel = privateChat(offline(NOW.minusSeconds(59)))

            assertEquals(status(res(R.string.chat_subtitle_last_seen_just_now)), viewModel.subtitle)
        }

    @Test
    fun `last seen within an hour counts the minutes, and follows the minute tick`() =
        runTest {
            val viewModel = privateChat(offline(NOW.minus(Duration.ofMinutes(5))))
            assertEquals(status(plural(R.plurals.chat_subtitle_last_seen_minutes, 5)), viewModel.subtitle)

            clock.setTime(NOW.plus(Duration.ofMinutes(2)))
            runCurrent()

            assertEquals(status(plural(R.plurals.chat_subtitle_last_seen_minutes, 7)), viewModel.subtitle)
        }

    @Test
    fun `last seen earlier today shows the time`() =
        runTest {
            // NOW is 09:41:30 UTC.
            val viewModel = privateChat(offline(Instant.parse("2026-10-18T07:05:00Z")))

            assertEquals(status(res(R.string.chat_subtitle_last_seen_today, "7:05 AM")), viewModel.subtitle)
        }

    @Test
    fun `last seen yesterday shows yesterday's time`() =
        runTest {
            val viewModel = privateChat(offline(Instant.parse("2026-10-17T21:30:00Z")))

            assertEquals(status(res(R.string.chat_subtitle_last_seen_yesterday, "9:30 PM")), viewModel.subtitle)
        }

    @Test
    fun `last seen earlier shows the date, with the year before this one`() =
        runTest {
            val thisYear = privateChat(offline(Instant.parse("2026-03-17T12:00:00Z")))
            assertEquals(status(res(R.string.chat_subtitle_last_seen_date, "Mar 17")), thisYear.subtitle)

            users.setUsers(user(ADA, status = offline(Instant.parse("2025-03-17T12:00:00Z"))))
            runCurrent()

            assertEquals(status(res(R.string.chat_subtitle_last_seen_date, "3/17/2025")), thisYear.subtitle)
        }

    @Test
    fun `a midnight tick turns today into yesterday`() =
        runTest {
            val viewModel = privateChat(offline(Instant.parse("2026-10-18T07:05:00Z")))

            clock.setTime(Instant.parse("2026-10-19T00:00:00Z"))
            runCurrent()

            assertEquals(status(res(R.string.chat_subtitle_last_seen_yesterday, "7:05 AM")), viewModel.subtitle)
        }

    @Test
    fun `hidden times show their buckets`() =
        runTest {
            val viewModel = privateChat(UserStatus.Recently)
            val buckets =
                listOf(
                    UserStatus.Recently to R.string.chat_subtitle_last_seen_recently,
                    UserStatus.LastWeek to R.string.chat_subtitle_last_seen_week,
                    UserStatus.LastMonth to R.string.chat_subtitle_last_seen_month,
                    UserStatus.Empty to R.string.chat_subtitle_last_seen_long_ago,
                )

            val subtitles =
                buckets.map { (status, _) ->
                    users.setUsers(user(ADA, status = status))
                    runCurrent()
                    viewModel.subtitle
                }

            assertEquals(buckets.map { (_, text) -> status(res(text)) }, subtitles)
        }

    @Test
    fun `a bot is a bot`() =
        runTest {
            users.setUsers(user(ADA, type = UserType.Bot))
            val viewModel = showing(chat(), history(message(1)))

            assertEquals(status(res(R.string.chat_subtitle_bot)), viewModel.subtitle)
        }

    @Test
    fun `a deleted account has its name and placeholder, and no subtitle`() =
        runTest {
            users.setUsers(user(ADA, type = UserType.Deleted))
            val viewModel = showing(chat(), history(message(1)))

            val header = viewModel.state.header!!
            assertEquals(res(UiR.string.user_deleted_account), header.title)
            assertEquals(UiR.drawable.ic_person_filled, header.placeholderIcon)
            assertNull(header.subtitle)
        }

    @Test
    fun `Saved Messages has its name and bookmark, and nobody typing`() =
        runTest {
            users.setUsers(user(ME))
            actions.setActions(ME, SenderAction(MessageSender.User(ME), ChatAction.Typing))
            val viewModel = showing(chat(id = ME), history(message(1, chatId = ME)))

            val header = viewModel.state.header!!
            assertEquals(res(UiR.string.chat_saved_messages), header.title)
            assertEquals(UiR.drawable.ic_bookmark_filled, header.placeholderIcon)
            assertNull(header.photo)
            assertNull(header.subtitle)
        }

    @Test
    fun `typing replaces the status of a user`() =
        runTest {
            actions.setActions(PRIVATE_CHAT, SenderAction(MessageSender.User(ADA), ChatAction.Typing))

            val viewModel = privateChat(UserStatus.Online(NOW.epochSecond + 60))

            assertEquals(ChatSubtitle.Action(res(UiR.string.action_typing)), viewModel.subtitle)
        }

    @Test
    fun `typing in a group names who types`() =
        runTest {
            users.setUsers(user(GRACE, firstName = "Grace"))
            actions.setActions(groupChat.id, SenderAction(MessageSender.User(GRACE), ChatAction.Typing))

            val viewModel = showing(groupChat, history(message(1, chatId = groupChat.id)))

            assertEquals(
                ChatSubtitle.Action(res(UiR.string.named_action_typing, UiText.Plain("Grace"))),
                viewModel.subtitle,
            )
        }

    @Test
    fun `a group counts its members`() =
        runTest {
            groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(memberCount = 12))

            val viewModel = showing(groupChat, history(message(1, chatId = groupChat.id)))

            assertEquals(status(plural(R.plurals.chat_subtitle_members, 12)), viewModel.subtitle)
        }

    @Test
    fun `a group whose member count is unknown is a group`() =
        runTest {
            val viewModel = showing(groupChat, history(message(1, chatId = groupChat.id)))

            assertEquals(status(res(R.string.chat_subtitle_group)), viewModel.subtitle)
        }

    @Test
    fun `a channel counts its subscribers`() =
        runTest {
            groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(memberCount = 1_024))

            val viewModel = showing(channelChat, history(message(1, chatId = channelChat.id)))

            assertEquals(status(plural(R.plurals.chat_subtitle_subscribers, 1_024)), viewModel.subtitle)
        }

    @Test
    fun `a supergroup counts its members, and a channel of unknown size is a channel`() =
        runTest {
            val supergroupChat =
                chat(id = -5, type = ChatType.Supergroup(ChatFixtures.SUPERGROUP_ID, isChannel = false))
            groups.supergroupMap.value =
                mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(memberCount = 300, isChannel = false))
            val group = showing(supergroupChat, history(message(1, chatId = -5)))
            assertEquals(status(plural(R.plurals.chat_subtitle_members, 300)), group.subtitle)

            groups.supergroupMap.value = emptyMap()
            val channel = showing(channelChat, history(message(1, chatId = channelChat.id)))

            assertEquals(status(res(R.string.chat_subtitle_channel)), channel.subtitle)
        }

    private fun TestScope.privateChat(status: UserStatus): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", status = status))
        return showing(chat(), history(message(1)))
    }

    private fun offline(at: Instant) = UserStatus.Offline(wasOnlineAtEpochSeconds = at.epochSecond)

    private fun status(text: UiText) = ChatSubtitle.Status(text)
}
