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
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeBirthdayReminderRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.ZoneId
import java.time.ZoneOffset

/** A birthday reminder ringing: its notification on the day, silence otherwise, and next year's ring. */
@RunWith(RobolectricTestRunner::class)
class BirthdayReminderWorkerTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val client = FakeTdlibClient()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val reminder = BirthdayReminder(userId = 42, day = 24, month = 11)
    private val reminders = FakeBirthdayReminderRepository(persistentListOf(reminder))
    private val scheduled = mutableListOf<Pair<BirthdayReminder, Instant>>()

    init {
        context.registerLauncherActivity()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels(context).ensureCreated()
    }

    private fun clockAt(now: Instant) =
        object : WallClock {
            override val minutes = MutableStateFlow(now)

            override fun now(): Instant = now

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private fun TestScope.account(locked: Set<Long> = emptySet()) =
        BirthdayAccountParts(
            reminders = reminders,
            auth = TdlibAuthRepository(client, backgroundScope, StandardTestDispatcher(testScheduler)),
            lockedChatIds = { locked },
            user = { id ->
                (TdlibUserRepository(client, backgroundScope).getUser(id) as? Outcome.Success)?.value
            },
        )

    private suspend fun runWorker(
        now: Instant = BIRTHDAY_MORNING,
        accounts: suspend (Int) -> BirthdayAccountParts?,
    ): ListenableWorker.Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = BirthdayReminderWorker(
                    context = appContext,
                    parameters = workerParameters,
                    accounts = accounts,
                    notifications = BirthdayReminderNotificationBuilder(appContext),
                    clock = clockAt(now),
                    schedule = { _, reminder, at -> scheduled += reminder to at },
                )
            }
        return TestListenableWorkerBuilder<BirthdayReminderWorker>(context, BirthdayReminderWorker.inputOf(0, reminder))
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    private fun loggedIn() {
        client.authState.value = AuthState.Ready
        client.on(TdApi.GetUser::class, TdResult.Success(TdApiFixtures.user(id = 42, firstName = "Ada")))
    }

    private fun posted() = shadowOf(manager).allNotifications.singleOrNull()

    private fun shown(): String {
        val extras = requireNotNull(posted()).extras
        return listOf(NotificationCompat.EXTRA_TITLE, NotificationCompat.EXTRA_TEXT)
            .joinToString(" ") { extras.getCharSequence(it).toString() }
            .filterNot { it in BIDI_ISOLATES }
    }

    @Test
    fun `on the day it rings with the contact's name, and next year's ring is scheduled`() =
        runTest {
            loggedIn()
            val account = account()

            assertEquals(ListenableWorker.Result.success(), runWorker { account })

            assertEquals(PingogramChannel.Reminders.id, requireNotNull(posted()).channelId)
            assertTrue(shown().startsWith("Ada's birthday"))
            assertEquals(listOf(reminder to Instant.parse("2027-11-24T09:00:00Z")), scheduled)
        }

    @Test
    fun `a locked chat's birthday names nothing`() =
        runTest {
            loggedIn()
            val account = account(locked = setOf(42L))

            runWorker { account }

            assertTrue("Ada" !in shown())
            assertEquals(NotificationCompat.VISIBILITY_PUBLIC, requireNotNull(posted()).visibility)
        }

    @Test
    fun `run on another day it stays silent but still schedules the next ring`() =
        runTest {
            loggedIn()
            val account = account()

            runWorker(now = Instant.parse("2026-11-25T09:00:00Z")) { account }

            assertNull(posted())
            assertEquals(listOf(reminder to Instant.parse("2027-11-24T09:00:00Z")), scheduled)
        }

    @Test
    fun `a reminder deleted or moved since it was scheduled stays silent and is not scheduled again`() =
        runTest {
            loggedIn()
            val account = account()

            reminders.all.value = persistentListOf(reminder.copy(day = 25))
            runWorker { account }
            reminders.all.value = persistentListOf()
            runWorker { account }

            assertNull(posted())
            assertTrue(scheduled.isEmpty())
        }

    @Test
    fun `an account that left the device rings nothing`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), runWorker { null })
            assertNull(posted())
        }

    private companion object {
        val BIRTHDAY_MORNING: Instant = Instant.parse("2026-11-24T09:00:00Z")
        const val BIDI_ISOLATES = "⁨⁩"
    }
}
