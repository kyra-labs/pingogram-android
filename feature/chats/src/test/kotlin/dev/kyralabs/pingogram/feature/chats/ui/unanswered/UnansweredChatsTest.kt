package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredDismissalRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredSettingsRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/**
 * The chats of the Unanswered page (`REQUIREMENTS.md` FR-X22): private chats where the other person wrote last and
 * had no answer for the chosen days, at most 30, the longest waiting first; never bots, groups, Saved Messages, the
 * user's own last message, locked chats, nor a chat dismissed with its last message.
 */
class UnansweredChatsTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val dismissals = FakeUnansweredDismissalRepository()
    private val settings = FakeUnansweredSettingsRepository(UnansweredSettings(afterDays = 3))
    private val unanswered =
        UnansweredChats(
            chats,
            users,
            FakeChatLockRepository(locked = setOf(LOCKED)),
            dismissals,
            settings,
            FakeWallClock(NOW),
        )

    /** Private chat [id] with a person whose last message, theirs unless [outgoing], came [ago]. */
    private fun privateChat(
        id: Long,
        ago: Duration,
        outgoing: Boolean = false,
        messageId: Long = id * 10,
    ) = chat(
        id = id,
        type = ChatType.Private(userId = id),
        lastMessage =
            message(
                chatId = id,
                id = messageId,
                sender = MessageSender.User(if (outgoing) ME else id),
                date = NOW.minus(ago.toJavaDuration()),
                isOutgoing = outgoing,
            ),
    )

    private suspend fun ids() = unanswered.chats.first().map { it.id }

    private fun lists() {
        users.setUsers(
            user(ADA),
            user(BOB),
            user(CAROL),
            user(BOT, type = UserType.Bot),
            user(OLD),
            user(LOCKED),
            user(EVE),
            user(ME),
        )
        chats.setChats(
            ChatListType.Main,
            privateChat(ADA, ago = 4.days),
            privateChat(BOB, ago = 1.days),
            privateChat(CAROL, ago = 5.days, outgoing = true),
            privateChat(BOT, ago = 5.days),
            privateChat(OLD, ago = 40.days),
            privateChat(LOCKED, ago = 5.days),
            privateChat(ME, ago = 6.days),
            chat(
                id = GROUP,
                type = ChatType.BasicGroup(1),
                lastMessage = message(chatId = GROUP, date = NOW.minus(5.days.toJavaDuration())),
            ),
            privateChat(EVE, ago = 10.days),
        )
    }

    @Test
    fun `the people who wrote last and waited the chosen days, the longest waiting first`() =
        runTest {
            lists()

            assertEquals(listOf(EVE, ADA), ids())
        }

    @Test
    fun `fewer days take in more chats`() =
        runTest {
            lists()
            settings.settings.value = UnansweredSettings(afterDays = 1)

            assertEquals(listOf(EVE, ADA, BOB), ids())
        }

    @Test
    fun `a dismissed chat leaves until a newer message comes`() =
        runTest {
            lists()
            dismissals.dismiss(ADA, ADA * 10)
            assertEquals(listOf(EVE), ids())

            chats.setChats(ChatListType.Main, privateChat(ADA, ago = 3.days, messageId = NEWER))

            assertEquals(listOf(ADA), ids())
        }

    @Test
    fun `the header offers the page while someone waits and its button is on`() =
        runTest {
            assertEquals(false, unanswered.offeredAt(flowOf(NOW)).first())

            lists()
            assertEquals(true, unanswered.offeredAt(flowOf(NOW)).first())

            settings.settings.value = UnansweredSettings(showButton = false)
            assertEquals(false, unanswered.offeredAt(flowOf(NOW)).first())
        }

    @Test
    fun `the rest of the main list loads, then it is loaded`() =
        runTest {
            assertEquals(listOf(false, true), unanswered.loaded.toList())
            assertEquals(ChatListType.Main, chats.loadRequests.single().first)
        }

    private companion object {
        const val ADA = 2L
        const val BOB = 3L
        const val CAROL = 4L
        const val BOT = 5L
        const val OLD = 6L
        const val LOCKED = 7L
        const val EVE = 8L
        const val GROUP = -100L
        const val NEWER = 999L
    }
}
