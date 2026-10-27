package dev.kyralabs.pingogram.background

import android.app.Notification
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [KeepConnectedService] on Robolectric: it runs in the foreground with its ongoing notification in the low-importance
 * "Connection" channel, and its notification's "Turn off" saves the setting as off and stops it. The app's graph (for
 * the setting) runs on a [FakeTdlibClient].
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class KeepConnectedServiceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = FakeTdlibClient()

    private val context = RuntimeEnvironment.getApplication()

    private fun entryPoint() =
        EntryPointAccessors.fromApplication(context, KeepConnectedService.KeepConnectedEntryPoint::class.java)

    @Test
    fun `runs in the foreground with an ongoing, silent notification in the connection channel`() {
        val controller = Robolectric.buildService(KeepConnectedService::class.java).create()

        controller.startCommand(0, 1)

        val shadow = shadowOf(controller.get())
        val notification = checkNotNull(shadow.lastForegroundNotification)
        assertEquals(KeepConnectedService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertEquals(KeepConnectedService.CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(
            context.getString(R.string.keep_connected_turn_off),
            NotificationCompat.getAction(notification, 0)?.title,
        )
        assertNotNull(notification.contentIntent)
        val channel =
            checkNotNull(
                NotificationManagerCompat.from(context).getNotificationChannelCompat(KeepConnectedService.CHANNEL_ID),
            )
        assertEquals(NotificationManagerCompat.IMPORTANCE_LOW, channel.importance)
        assertEquals(context.getString(R.string.keep_connected_channel), channel.name)
        assertTrue("Telegram" !in channel.name.toString())
    }

    @Test
    fun `turn off saves the setting as off and stops the service`() {
        hiltRule.inject()
        val repository = entryPoint().keepConnectedRepository()
        runBlocking { repository.setKeepConnected(true) }
        val controller = Robolectric.buildService(KeepConnectedService::class.java).create()
        controller.startCommand(0, 1)

        // The service's second command, as Android delivers it (ServiceController.withIntent is deprecated).
        controller.get().onStartCommand(
            Intent(context, KeepConnectedService::class.java).setAction(KeepConnectedService.ACTION_TURN_OFF),
            0,
            2,
        )

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        // The service saves on the application scope, so the write may still be in flight here. A DataStore (1.2.1)
        // collector that starts while a write is in flight can keep the old value until the next write, so read
        // afresh until the saved value shows rather than wait on one collection.
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (repository.keepConnected.first()) delay(POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}
