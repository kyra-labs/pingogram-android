package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationActionWorkerTest {
    private fun TestScope.harness(authState: AuthState = AuthState.Ready) =
        NotificationsHarness(this).apply {
            client.authState.value = authState
            client.on(TdApi.SendMessage::class, TdResult.Success(message()))
            client.on(TdApi.ViewMessages::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.RemoveNotificationGroup::class, TdResult.Success(TdApi.Ok()))
        }

    private suspend fun NotificationsHarness.run(
        action: NotificationAction,
        accounts: suspend (Int) -> AccountNotificationActions? = { AccountNotificationActions(auth, notifications) },
    ): ListenableWorker.Result {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = NotificationActionWorker(
                    appContext,
                    workerParameters,
                    accounts,
                    renderer,
                    builder,
                )
            }
        return TestListenableWorkerBuilder<NotificationActionWorker>(context, action.toData())
            .setWorkerFactory(factory)
            .build()
            .doWork()
    }

    @Test
    fun `a reply is sent, then the notification's messages are read`() =
        runTest {
            val harness = harness()

            val result =
                harness.run(
                    NotificationAction.Reply(7, 100, listOf(10, 11), replyToMessageId = null, text = "Hi"),
                )

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(
                listOf("SendMessage", "ViewMessages"),
                // GetChat: whether the chat is secret decides its link previews.
                harness.client.sent
                    .map { it.javaClass.simpleName }
                    .filter { it != "SetOption" && it != "GetChat" },
            )
            assertEquals(
                listOf(10L, 11L),
                harness.client
                    .sentOf<TdApi.ViewMessages>()
                    .single()
                    .messageIds
                    .toList(),
            )
        }

    @Test
    fun `a reply TDLib refuses reads nothing and fails`() =
        runTest {
            val harness = harness()
            harness.client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(400, "CHAT_WRITE_FORBIDDEN")))

            val result = harness.run(NotificationAction.Reply(7, 100, listOf(10), replyToMessageId = null, text = "Hi"))

            assertEquals(ListenableWorker.Result.failure(), result)
            assertTrue(harness.client.sentOf<TdApi.ViewMessages>().isEmpty())
        }

    @Test
    fun `mark as read and dismiss reach TDLib`() =
        runTest {
            val harness = harness()

            assertEquals(
                ListenableWorker.Result.success(),
                harness.run(NotificationAction.MarkAsRead(7, 100, listOf(10))),
            )
            assertEquals(
                ListenableWorker.Result.success(),
                harness.run(NotificationAction.Dismiss(7, maxNotificationId = 3)),
            )
            assertTrue(
                harness.client
                    .sentOf<TdApi.ViewMessages>()
                    .single()
                    .forceRead,
            )
            assertEquals(
                3,
                harness.client
                    .sentOf<TdApi.RemoveNotificationGroup>()
                    .single()
                    .maxNotificationId,
            )
        }

    @Test
    fun `nothing is sent when nobody is logged in`() =
        runTest {
            val harness = harness(authState = AuthState.WaitPhoneNumber)

            val result = harness.run(NotificationAction.MarkAsRead(7, 100, listOf(10)))

            assertEquals(ListenableWorker.Result.failure(), result)
            assertTrue(harness.client.sentOf<TdApi.ViewMessages>().isEmpty())
        }

    @Test
    fun `an action goes to the account of its notification`() =
        runTest {
            val harness = harness()
            val asked = mutableListOf<Int>()

            val result =
                harness.run(NotificationAction.MarkAsRead(7, 100, listOf(10), slot = 2)) { slot ->
                    asked += slot
                    AccountNotificationActions(harness.auth, harness.notifications)
                }

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(listOf(2), asked)
        }

    @Test
    fun `an action of an account that left the device does nothing`() =
        runTest {
            val harness = harness()

            val result = harness.run(NotificationAction.MarkAsRead(7, 100, listOf(10), slot = 1)) { null }

            assertEquals(ListenableWorker.Result.failure(), result)
            assertTrue(harness.client.sent.none { it is TdApi.ViewMessages })
        }
}
