package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.domain.model.Transfers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The transfers notification on Robolectric's Android 14: the standard progress bar, silent, with "Cancel". */
@RunWith(RobolectricTestRunner::class)
class TransferNotificationBuilderTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val builder = TransferNotificationBuilder(context)
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        context.registerLauncherActivity()
    }

    @Test
    fun `says how many files are on their way and how much of them, in the transfers channel`() {
        val notification = builder.build(Transfers(uploads = 2, transferredSize = 250, totalSize = 1_000))

        assertEquals(PingogramChannel.Transfers.id, notification.channelId)
        assertEquals("Sending 2 files", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(
            notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .contains(" of "),
        )
        assertEquals(
            TransferNotificationBuilder.PROGRESS_MAX,
            notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX),
        )
        assertEquals(250, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(false, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(NotificationCompat.CATEGORY_PROGRESS, notification.category)
    }

    @Test
    fun `names downloads, and both kinds together`() {
        val downloading = builder.build(Transfers(downloads = 1, totalSize = 10))
        val both = builder.build(Transfers(uploads = 1, downloads = 2, totalSize = 10))

        assertEquals("Downloading 1 file", downloading.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Sending and downloading 3 files", both.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `an unknown size shows an indeterminate bar and no sizes`() {
        val notification = builder.build(Transfers(downloads = 1))

        assertTrue(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun `Cancel reaches the app's own receiver`() {
        val notification = builder.build(Transfers(uploads = 1, totalSize = 10))

        val cancel = notification.actions.single()
        assertEquals("Cancel", cancel.title.toString())
        val intent = shadowOf(cancel.actionIntent).savedIntent
        assertEquals(TransferCancelReceiver::class.java.name, intent.component?.className)
        assertEquals(TransferCancelReceiver.ACTION_CANCEL, intent.action)
    }

    @Test
    fun `updating posts only with the permission to post`() {
        builder.update(Transfers(uploads = 1, totalSize = 10))
        assertNull(shadowOf(manager).getNotification(TransferNotificationBuilder.NOTIFICATION_ID))

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        builder.update(Transfers(uploads = 1, totalSize = 10))
        assertEquals(
            "Sending 1 file",
            shadowOf(manager)
                .getNotification(TransferNotificationBuilder.NOTIFICATION_ID)
                .extras
                .getString(Notification.EXTRA_TITLE),
        )
    }
}
