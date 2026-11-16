package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.testing.FakeKeywordAlertsRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Keyword alerts' notifications: a muted chat's match alerts in its own channel; nothing else does. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
@RunWith(RobolectricTestRunner::class)
class KeywordAlertNotifierTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alerts = MutableSharedFlow<KeywordAlert>(extraBufferCapacity = 4)
    private val chats = mutableMapOf(1L to MutableStateFlow<Chat?>(chat(1, isMuted = true)))
    private val settings = FakeKeywordAlertsRepository(KeywordAlertSettings(words = persistentListOf("launch")))
    private var hidden = emptySet<Long>()
    private var open = emptySet<Long>()

    init {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels(context).ensureCreated()
    }

    private fun TestScope.start() {
        val account =
            KeywordAlertAccount(
                slot = 0,
                alerts = alerts,
                hiddenChatIds = { hidden },
                openChatIds = { open },
                chat = { id -> chats[id] ?: flowOf(null) },
            )
        KeywordAlertNotifier(context, flowOf(listOf(account)), settings, { _, _ -> null }, backgroundScope).start()
        runCurrent()
    }

    private suspend fun TestScope.match(
        messageId: Long = 10,
        isMuted: Boolean = true,
    ) {
        alerts.emit(
            KeywordAlert(
                hit = KeywordHit(chatId = 1, messageId = messageId, word = "launch", date = 1_800_000_000),
                text = "The launch is today",
                start = 4,
                end = 10,
                isMuted = isMuted,
            ),
        )
        runCurrent()
    }

    private fun posted() = manager.activeNotifications.filter { it.tag == KeywordAlertNotifier.TAG }

    @Test
    fun `a muted chat's match alerts in the keyword alerts channel, the word in bold`() =
        runTest {
            start()
            match()

            val notification = posted().single().notification
            assertEquals(PingogramChannel.KeywordAlerts.id, notification.channelId)
            assertEquals("Chat 1", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
            assertEquals(
                "Keyword: launch",
                notification.extras
                    .getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)
                    .toString()
                    .filter { it.isLetterOrDigit() || it == ' ' || it == ':' },
            )
            val text = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT) as Spanned
            val bold = text.getSpans(0, text.length, StyleSpan::class.java).single()
            assertEquals("launch", text.substring(text.getSpanStart(bold), text.getSpanEnd(bold)))
            assertEquals(listOf("Open", "Mark as read"), notification.actions.map { it.title.toString() })
            for (intent in listOf(notification.contentIntent, notification.actions.first().actionIntent)) {
                val opened = shadowOf(intent).savedIntent
                assertEquals(1L, NotificationIntents.chatIdOf(opened))
                assertEquals(10L, NotificationIntents.messageIdOf(opened))
            }
        }

    @Test
    fun `a match of a chat that is not muted, locked, on screen, or with the setting off posts nothing`() =
        runTest {
            start()
            match(isMuted = false)
            hidden = setOf(1)
            match(messageId = 11)
            hidden = emptySet()
            open = setOf(1)
            match(messageId = 12)
            open = emptySet()
            settings.setAlertWhenMuted(false)
            match(messageId = 13)

            assertTrue(posted().isEmpty())
        }

    @Test
    fun `several matches group, and a read message's alert goes`() =
        runTest {
            start()
            match(messageId = 10)
            match(messageId = 11)
            assertEquals(3, posted().size)

            chats.getValue(1).value = chats.getValue(1).value?.copy(lastReadInboxMessageId = 11)
            runCurrent()

            assertTrue(posted().isEmpty())
        }

    private companion object {
        fun chat(
            id: Long,
            isMuted: Boolean,
        ) = Chat(
            id = id,
            type = ChatType.BasicGroup(basicGroupId = id),
            title = "Chat $id",
            photo = null,
            lastMessage = null,
            positions = persistentListOf(),
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
