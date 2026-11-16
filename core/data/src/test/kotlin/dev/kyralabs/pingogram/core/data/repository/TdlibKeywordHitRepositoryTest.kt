package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** New messages matched with the keyword alert words on this phone, kept for a week in a file, gone at logout. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class TdlibKeywordHitRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()
    private val settings = MutableStateFlow(KeywordAlertSettings(words = persistentListOf("launch", "café")))
    private val chats = mutableMapOf(1L to chat(1), 2L to chat(2, isMuted = true, folder = 5))

    private val file get() = File(directory, TdlibKeywordHitRepository.FILE_NAME)

    private fun TestScope.repository() =
        TdlibKeywordHitRepository(
            client = client,
            settings = settings,
            chatOf = { chats[it] },
            clock = Clock,
            scope = backgroundScope,
            file = file,
            io = StandardTestDispatcher(testScheduler),
        )

    private suspend fun TestScope.receive(
        id: Long,
        chatId: Long = 1,
        text: String = "The launch is today",
        outgoing: Boolean = false,
        date: Long = NOW,
    ) {
        val content = messageText(text)
        client.emit(
            TdApi.UpdateNewMessage(
                message(id = id, chatId = chatId, content = content, isOutgoing = outgoing, date = date.toInt()),
            ),
        )
        runCurrent()
    }

    @Test
    fun `a message with a word is a match, announced with where the word is`() =
        runTest {
            val repository = repository()
            val alerts = mutableListOf<KeywordAlert>()
            backgroundScope.launch { repository.alerts.collect(alerts::add) }
            runCurrent()

            receive(id = 10, text = "The LAUNCH is today")

            assertEquals(
                listOf(KeywordHit(chatId = 1, messageId = 10, word = "launch", date = NOW)),
                repository.recent.first(),
            )
            val alert = alerts.single()
            assertEquals("LAUNCH", alert.text.substring(alert.start, alert.end))
            assertEquals(false, alert.isMuted)
        }

    @Test
    fun `the user's own messages, other words and parts of words do not match`() =
        runTest {
            val repository = repository()
            runCurrent()

            receive(id = 10, outgoing = true)
            receive(id = 11, text = "Relaunched yesterday")
            receive(id = 12, text = "Lunch at noon")

            assertTrue(repository.recent.first().isEmpty())
        }

    @Test
    fun `only the chats the settings look at match`() =
        runTest {
            val repository = repository()
            settings.value = settings.value.copy(where = KeywordScope.MutedChats)
            runCurrent()
            receive(id = 10, chatId = 1)
            receive(id = 11, chatId = 2)
            assertEquals(listOf(11L), repository.recent.first().map { it.messageId })

            settings.value = settings.value.copy(where = KeywordScope.Folders(persistentSetOf(7)))
            runCurrent()
            receive(id = 12, chatId = 2)
            assertEquals(listOf(11L), repository.recent.first().map { it.messageId })
        }

    @Test
    fun `matches survive the process, older than a week ones go, and a read chat's are forgotten`() =
        runTest {
            val repository = repository()
            runCurrent()
            receive(id = 10, chatId = 1, date = NOW - TdlibKeywordHitRepository.RECENT_SECONDS - 1)
            receive(id = 11, chatId = 1)
            receive(id = 12, chatId = 2, text = "Meet at the Cafe")

            val again = repository()
            runCurrent()
            assertEquals(
                listOf(11L, 12L),
                again.recent
                    .first()
                    .map { it.messageId }
                    .sorted(),
            )
            assertEquals(
                "café",
                again.recent
                    .first()
                    .first { it.messageId == 12L }
                    .word,
            )

            again.clearChat(1)
            assertEquals(listOf(12L), again.recent.first().map { it.messageId })
        }

    @Test
    fun `logging out deletes the matches`() =
        runTest {
            val repository = repository()
            runCurrent()
            receive(id = 10)

            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateLoggingOut()))
            runCurrent()

            assertTrue(repository.recent.first().isEmpty())
            assertTrue(!file.exists())
        }

    private object Clock : WallClock {
        override fun now(): Instant = Instant.ofEpochSecond(NOW)

        override fun zone(): ZoneId = ZoneOffset.UTC

        override val minutes = MutableStateFlow(now())
    }

    private companion object {
        const val NOW = 1_800_000_000L

        fun chat(
            id: Long,
            isMuted: Boolean = false,
            folder: Int? = null,
        ) = Chat(
            id = id,
            type = ChatType.BasicGroup(basicGroupId = id),
            title = "Chat $id",
            photo = null,
            lastMessage = null,
            positions =
                listOfNotNull(folder?.let { ChatPosition(ChatListType.Folder(it), order = 1, isPinned = false) })
                    .toImmutableList(),
            unreadCount = 0,
            unreadMentionCount = 0,
            unreadReactionCount = 0,
            lastReadInboxMessageId = 0,
            lastReadOutboxMessageId = 0,
            isMarkedAsUnread = false,
            isMuted = isMuted,
            draft = null,
            hasProtectedContent = false,
            messageAutoDeleteTime = 0,
            permissions = ChatPermissions.All,
        )
    }
}
