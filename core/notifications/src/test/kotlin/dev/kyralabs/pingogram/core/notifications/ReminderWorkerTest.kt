package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.SecretChatLinkPreviews
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/** A message reminder ringing: its notification, and when it stays silent. */
@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val client = FakeTdlibClient()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val reminder = MessageReminder(chatId = 100, messageId = 1, at = Instant.ofEpochSecond(5_000), "Call back")
    private val reminders = InMemoryReminders(listOf(reminder))

    init {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels(context).ensureCreated()
    }

    private fun TestScope.account(
        locked: Set<Long> = emptySet(),
        userName: (Long) -> String? = { null },
    ) = ReminderAccountParts(
        reminders = reminders,
        auth = TdlibAuthRepository(client, backgroundScope, StandardTestDispatcher(testScheduler)),
        chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats()),
        messages =
            TdlibMessageRepository(
                client,
                backgroundScope,
                SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
            ),
        lockedChatIds = { locked },
        userName = { userName(it) },
    )

    private suspend fun runWorker(
        scheduled: MessageReminder = reminder,
        accounts: suspend (Int) -> ReminderAccountParts?,
    ): ListenableWorker.Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = ReminderWorker(appContext, workerParameters, accounts, ReminderNotificationBuilder(appContext))
            }
        return TestListenableWorkerBuilder<ReminderWorker>(context, ReminderWorker.inputOf(slot = 0, scheduled))
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private fun loggedInWithMessage() {
        client.authState.value = AuthState.Ready
        client.on(TdApi.GetChat::class, TdResult.Success(TdApiFixtures.chat(id = 100, title = "Ada Lovelace")))
        client.on(
            TdApi.GetMessage::class,
            TdResult.Success(
                TdApiFixtures.message(id = 1, chatId = 100, content = TdApiFixtures.messageText("Lunch?")),
            ),
        )
    }

    private fun posted() = shadowOf(manager).allNotifications.singleOrNull()

    @Test
    fun `a reminder rings with the chat, the message and the note, in its own channel`() =
        runTest {
            loggedInWithMessage()
            val account = account()

            assertEquals(ListenableWorker.Result.success(), runWorker { account })

            val notification = requireNotNull(posted())
            assertEquals(PingogramChannel.Reminders.id, notification.channelId)
            val extras = notification.extras
            assertTrue(extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString().contains("Ada Lovelace"))
            assertEquals("Lunch?", extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
            assertEquals("Lunch?\nCall back", extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT).toString())
            assertEquals(listOf("Open", "Snooze 1 h"), notification.actions.map { it.title.toString() })
        }

    @Test
    fun `in a group the message follows its sender's name`() =
        runTest {
            loggedInWithMessage()
            client.on(
                TdApi.GetChat::class,
                TdResult.Success(TdApiFixtures.chat(id = 100, type = TdApi.ChatTypeBasicGroup(7), title = "Trip")),
            )
            val account = account(userName = { if (it == 42L) "Aisha" else null })

            runWorker { account }

            val text = requireNotNull(posted()).extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
            assertEquals("Aisha: Lunch?", text.filterNot { it in BIDI_ISOLATES })
        }

    @Test
    fun `a locked chat's reminder names nothing of the chat and opens the locked chats`() =
        runTest {
            loggedInWithMessage()
            val account = account(locked = setOf(100L))

            runWorker { account }

            val notification = requireNotNull(posted())
            val extras = notification.extras
            assertEquals("Reminder", extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
            assertEquals(
                context.getString(R.string.reminder_notification_locked),
                extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
            )
            val shown =
                listOf(NotificationCompat.EXTRA_TITLE, NotificationCompat.EXTRA_TEXT, NotificationCompat.EXTRA_BIG_TEXT)
                    .joinToString { extras.getCharSequence(it).toString() }
            assertTrue("Ada Lovelace" !in shown && "Lunch?" !in shown && "Call back" !in shown)
            assertNull(notification.getLargeIcon())
            assertEquals(NotificationCompat.VISIBILITY_PUBLIC, notification.visibility)
        }

    @Test
    fun `a deleted message still rings, saying so`() =
        runTest {
            loggedInWithMessage()
            client.on(TdApi.GetMessage::class, TdResult.Failure(TdError(404, "Not Found")))
            val account = account()

            runWorker { account }

            val text = requireNotNull(posted()).extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
            assertEquals(context.getString(R.string.reminder_notification_deleted), text)
        }

    @Test
    fun `a reminder done, deleted or moved since it was scheduled stays silent`() =
        runTest {
            loggedInWithMessage()
            val account = account()

            reminders.list.value = listOf(reminder.copy(isDone = true))
            runWorker { account }
            reminders.list.value = listOf(reminder.copy(at = Instant.ofEpochSecond(9_000)))
            runWorker { account }
            reminders.list.value = emptyList()
            runWorker { account }

            assertNull(posted())
        }

    @Test
    fun `an account that left the device rings nothing`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), runWorker { null })
            assertNull(posted())
        }

    private companion object {
        /** Unicode's first-strong isolate and pop directional isolate, which wrap names in built strings. */
        const val BIDI_ISOLATES = "\u2068\u2069"
    }

    /** Reminders in memory, set as a test needs them. */
    private class InMemoryReminders(
        initial: List<MessageReminder>,
    ) : ReminderRepository {
        val list = MutableStateFlow(initial)

        override val reminders: Flow<ImmutableList<MessageReminder>> = list.map { it.toImmutableList() }

        override fun reminders(chatId: Long): Flow<ImmutableMap<Long, MessageReminder>> =
            list.map { all -> all.filter { it.chatId == chatId }.associateBy { it.messageId }.toImmutableMap() }

        override suspend fun set(reminder: MessageReminder): Outcome<Unit> {
            list.value = list.value.filterNot { it.messageId == reminder.messageId } + reminder
            return Outcome.Success(Unit)
        }

        override suspend fun delete(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> {
            list.value = list.value.filterNot { it.messageId == messageId }
            return Outcome.Success(Unit)
        }
    }
}
