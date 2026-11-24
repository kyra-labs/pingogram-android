package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

/** A birthday reminder's notification: named in the Reminders channel, and naming nothing for a locked chat. */
@RunWith(RobolectricTestRunner::class)
class BirthdayReminderNotificationBuilderTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val builder = BirthdayReminderNotificationBuilder(context)
    private val at = Instant.parse("2026-11-24T09:00:00Z")

    init {
        context.registerLauncherActivity()
        NotificationChannels(context).ensureCreated()
    }

    private fun shown(notification: android.app.Notification): String =
        listOf(NotificationCompat.EXTRA_TITLE, NotificationCompat.EXTRA_TEXT)
            .joinToString(" ") { notification.extras.getCharSequence(it).toString() }
            .filterNot { it in BIDI_ISOLATES }

    @Test
    fun `it names the contact, rings as a reminder and opens their chat`() {
        val notification = builder.build(slot = 0, userId = 42, name = "Ada", locked = false, at = at)

        assertEquals(PingogramChannel.Reminders.id, notification.channelId)
        assertEquals(NotificationCompat.CATEGORY_REMINDER, notification.category)
        assertTrue(shown(notification).startsWith("Ada's birthday"))
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.contentIntent != null)
    }

    @Test
    fun `a locked chat's or an unnamed contact's names nothing and shows on the lock screen`() {
        listOf(
            builder.build(slot = 0, userId = 42, name = "Ada", locked = true, at = at),
            builder.build(slot = 0, userId = 42, name = null, locked = false, at = at),
        ).forEach { notification ->
            assertTrue("Ada" !in shown(notification))
            assertTrue(shown(notification).startsWith("A birthday today"))
            assertEquals(NotificationCompat.VISIBILITY_PUBLIC, notification.visibility)
        }
    }

    private companion object {
        const val BIDI_ISOLATES = "⁨⁩"
    }
}
