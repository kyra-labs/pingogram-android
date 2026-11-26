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
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.testing.FakeDailyDigestSettingsRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The Daily digest's work (`REQUIREMENTS.md` FR-X23): it posts the digest and schedules the next day's while on. */
@RunWith(RobolectricTestRunner::class)
class DailyDigestWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settings = FakeDailyDigestSettingsRepository(DailyDigestSettings(enabled = true))
    private val scheduled = mutableListOf<LocalTime>()

    init {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels(context).ensureCreated()
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private suspend fun runWorker(vararg accounts: DigestAccountParts): ListenableWorker.Result {
        val reader = DailyDigestReader({ accounts.toList() }, { false }, { false }, clock)
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = DailyDigestWorker(
                    context = appContext,
                    parameters = workerParameters,
                    settings = settings,
                    read = reader::read,
                    post = { DailyDigestNotificationBuilder(appContext).post(it, NOW) },
                    scheduleNext = { scheduled += it },
                )
            }
        return TestListenableWorkerBuilder<DailyDigestWorker>(context)
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private fun posted() = shadowOf(manager).allNotifications.singleOrNull()

    @Test
    fun `while on it posts the signed-in accounts' digest and schedules the next one`() =
        runTest {
            val signedIn = DigestAccountParts(loggedIn = { true }, chats = { listOf(chat(1), chat(2)) })
            val signedOut = DigestAccountParts(loggedIn = { false }, chats = { listOf(chat(3)) })

            assertEquals(ListenableWorker.Result.success(), runWorker(signedIn, signedOut))

            val notification = requireNotNull(posted())
            assertEquals(PingogramChannel.DailyDigest.id, notification.channelId)
            val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString()
            assertEquals("2 unread chats", title)
            assertEquals(listOf(LocalTime.of(8, 30)), scheduled)
        }

    @Test
    fun `with nothing unread it posts nothing, and still schedules the next one`() =
        runTest {
            runWorker(DigestAccountParts(loggedIn = { true }, chats = { emptyList() }))

            assertNull(posted())
            assertEquals(1, scheduled.size)
        }

    @Test
    fun `it counts once TDLib has caught up with the server`() =
        runTest {
            var synced = false
            val account =
                DigestAccountParts(
                    loggedIn = { true },
                    synced = { synced = true },
                    chats = { if (synced) listOf(chat(1), chat(2)) else listOf(chat(1)) },
                )

            runWorker(account)

            val title = requireNotNull(posted()).extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString()
            assertEquals("2 unread chats", title)
        }

    @Test
    fun `offline, it counts what the phone has once the wait is over`() =
        runTest {
            val offline =
                DigestAccountParts(loggedIn = { true }, synced = { awaitCancellation() }, chats = { listOf(chat(1)) })

            runWorker(offline)

            assertEquals("1 unread chat", posted()?.extras?.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        }

    @Test
    fun `a digest that cannot be read still schedules the next one`() =
        runTest {
            val broken = DigestAccountParts(loggedIn = { true }, chats = { throw IllegalStateException("database") })

            assertTrue(runCatching { runWorker(broken) }.isFailure)

            assertEquals(listOf(LocalTime.of(8, 30)), scheduled)
        }

    @Test
    fun `turned off meanwhile it posts and schedules nothing`() =
        runTest {
            settings.settings.value = DailyDigestSettings(enabled = false)

            runWorker(DigestAccountParts(loggedIn = { true }, chats = { listOf(chat(1)) }))

            assertNull(posted())
            assertTrue(scheduled.isEmpty())
        }

    private fun chat(id: Long) =
        Chat(
            id = id,
            type = ChatType.BasicGroup(basicGroupId = id),
            title = "Chat $id",
            photo = null,
            lastMessage = null,
            positions = persistentListOf(),
            unreadCount = 1,
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

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-26T09:00:00Z")
    }
}
