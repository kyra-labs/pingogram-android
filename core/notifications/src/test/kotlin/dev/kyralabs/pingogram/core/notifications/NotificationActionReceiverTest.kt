package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [NotificationActionReceiver] hands each action to [NotificationActionWorker], expedited. */
@RunWith(RobolectricTestRunner::class)
class NotificationActionReceiverTest {
    private val context = RuntimeEnvironment.getApplication()
    private val received = mutableListOf<Pair<String, Data>>()

    @Before
    fun setUp() {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    received += workerClassName to workerParameters.inputData
                    return null
                }
            }
        val configuration =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @Test
    fun `a reply becomes expedited work for the worker, with the text typed`() {
        val broadcast = shadowOf(NotificationAction.markAsReadIntent(context, conversation())).savedIntent
        val reply = Intent(shadowOf(NotificationAction.replyIntent(context, conversation())).savedIntent)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(NotificationAction.KEY_TEXT).build()),
            reply,
            Bundle().apply { putCharSequence(NotificationAction.KEY_TEXT, "Hi") },
        )

        NotificationActionReceiver().onReceive(context, reply)
        NotificationActionReceiver().onReceive(context, broadcast)

        val work = WorkManager.getInstance(context).getWorkInfosByTag(NotificationActionReceiver.WORK_TAG).get()
        assertEquals(2, work.size)
        assertEquals(
            listOf(
                NotificationAction.Reply(7, 100, listOf(10, 11), replyToMessageId = null, text = "Hi"),
                NotificationAction.MarkAsRead(7, 100, listOf(10, 11)),
            ),
            received.map { (worker, data) ->
                assertEquals(NotificationActionWorker::class.java.name, worker)
                NotificationAction.fromData(data)
            },
        )
    }

    @Test
    fun `an intent of no action enqueues nothing`() {
        NotificationActionReceiver().onReceive(context, Intent(context, NotificationActionReceiver::class.java))

        assertTrue(
            WorkManager
                .getInstance(context)
                .getWorkInfosByTag(NotificationActionReceiver.WORK_TAG)
                .get()
                .isEmpty(),
        )
    }

    private fun conversation() =
        ConversationNotification(
            groupId = 7,
            chatId = 100,
            channel = PingogramChannel.PrivateChats,
            title = "Ada",
            isGroupConversation = false,
            isCall = false,
            messages = emptyList(),
            totalCount = 2,
            canReply = true,
            replyToMessageId = null,
            messageIds = listOf(10, 11),
            maxNotificationId = 2,
            chatIcon =
                androidx.core.graphics.drawable.IconCompat.createWithResource(
                    context,
                    R.drawable.ic_notification,
                ),
            chatPerson = null,
        )
}
