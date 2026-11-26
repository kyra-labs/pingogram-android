package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The chats Cleanup suggestions offer (`REQUIREMENTS.md` FR-X24): main list chats not opened for the chosen months,
 * the longest unopened first, never pinned, locked, secret, Saved Messages nor Telegram's own; none while off.
 */
class CleanupChatsTest {
    private val settings = MutableStateFlow(CleanupSettings(months = 3))
    private val openings =
        MutableStateFlow(
            ChatOpenings(
                since = BEGUN,
                lastOpened = persistentMapOf(ADA to NOW.minusSeconds(DAY), BOB to JUNE),
            ),
        )

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val chats =
        listOf(
            chat(ADA),
            chat(BOB),
            chat(GROUP, type = ChatType.BasicGroup(1)),
            chat(PINNED, pinned = true),
            chat(LOCKED),
            chat(SECRET, type = ChatType.Secret(secretChatId = 2, userId = SECRET)),
            chat(ME),
            chat(CleanupRule.SERVICE_NOTIFICATIONS),
            chat(SUPPORT),
        )

    private val cleanup =
        CleanupChats(
            CleanupSources(
                chats = flowOf(chats),
                users = flowOf(mapOf(SUPPORT to user(SUPPORT, isSupport = true), BOB to user(BOB))),
                me = flowOf(ME),
                locked = flowOf(setOf(LOCKED)),
                openings = openings,
                loadMore = { false },
            ),
            settings,
            clock,
        )

    private suspend fun ids() = cleanup.chats.first().map { it.id }

    @Test
    fun `the chats not opened for the months chosen, the longest unopened first, the others left out`() =
        runTest {
            // Never opened since the record began in January: as unopened as can be, before Bob's June.
            assertEquals(listOf(GROUP, BOB), ids())
        }

    @Test
    fun `fewer months suggest more, and none while the record is younger than them`() =
        runTest {
            settings.value = CleanupSettings(months = 1)
            assertEquals(listOf(GROUP, BOB), ids())

            openings.value = ChatOpenings(since = NOW.minusSeconds(DAY))
            assertEquals(emptyList<Long>(), ids())
        }

    @Test
    fun `turned off, nothing is suggested`() =
        runTest {
            settings.value = CleanupSettings(enabled = false)

            assertEquals(emptyList<Long>(), ids())
        }

    @Test
    fun `the rest of the main list loads, then it is loaded`() =
        runTest {
            assertEquals(listOf(false, true), cleanup.loaded.toList())
        }

    private fun chat(
        id: Long,
        type: ChatType = ChatType.Private(userId = id),
        pinned: Boolean = false,
    ) = Chat(
        id = id,
        type = type,
        title = "Chat $id",
        photo = null,
        lastMessage = null,
        positions = persistentListOf(ChatPosition(ChatListType.Main, order = id, isPinned = pinned)),
        unreadCount = 0,
        unreadMentionCount = 0,
        unreadReactionCount = 0,
        lastReadInboxMessageId = 0,
        lastReadOutboxMessageId = 0,
        isMarkedAsUnread = false,
        isMuted = false,
        draft = null,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
        permissions = ChatPermissions.All,
    )

    private fun user(
        id: Long,
        isSupport: Boolean = false,
    ) = User(
        id = id,
        firstName = "User $id",
        lastName = "",
        usernames = persistentListOf(),
        phoneNumber = "",
        status = UserStatus.Empty,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = isSupport,
        type = UserType.Regular,
        haveAccess = true,
    )

    private companion object {
        const val ADA = 2L
        const val BOB = 3L
        const val GROUP = -100L
        const val PINNED = 4L
        const val LOCKED = 5L
        const val SECRET = 6L
        const val ME = 1L
        const val SUPPORT = 8L
        const val DAY = 86_400L
        val BEGUN: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val JUNE: Instant = Instant.parse("2026-06-01T00:00:00Z")
        val NOW: Instant = Instant.parse("2026-11-26T09:00:00Z")
    }
}
