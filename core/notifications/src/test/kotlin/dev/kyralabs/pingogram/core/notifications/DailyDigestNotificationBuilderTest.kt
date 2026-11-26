package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/** The Daily digest's notification (`REQUIREMENTS.md` FR-X23): quiet, its counts over names, opening Catch-up. */
@RunWith(RobolectricTestRunner::class)
class DailyDigestNotificationBuilderTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val builder = DailyDigestNotificationBuilder(context)
    private val at = Instant.parse("2026-11-26T08:30:00Z")

    init {
        context.registerLauncherActivity()
        NotificationChannels(context).ensureCreated()
    }

    private fun android.app.Notification.text(key: String) =
        extras.getCharSequence(key).toString().filterNot { it in BIDI_ISOLATES }

    @Test
    fun `its counts over the names, quiet in its own channel, private, opening catch-up`() {
        val notification =
            builder.build(DailyDigest(7, mentionChats = 1, reminders = 2, names = listOf("Ada", "Bob")), at)

        assertEquals(PingogramChannel.DailyDigest.id, notification.channelId)
        assertEquals(
            "7 unread chats · 1 mentions you · 2 reminders today",
            notification.text(NotificationCompat.EXTRA_TITLE),
        )
        assertEquals("Ada, Bob and 5 more", notification.text(NotificationCompat.EXTRA_TEXT))
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        val opened = shadowOf(notification.contentIntent).savedIntent
        assertTrue(CatchUpIntents.opensCatchUp(opened))
    }

    @Test
    fun `without names it leaves the empty counts out, says to catch up, and may show on the lock screen`() {
        val notification = builder.build(DailyDigest(0, mentionChats = 0, reminders = 1, names = emptyList()), at)

        assertEquals("1 reminder today", notification.text(NotificationCompat.EXTRA_TITLE))
        assertEquals("Tap to catch up", notification.text(NotificationCompat.EXTRA_TEXT))
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, notification.visibility)
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun `the channel is quiet and shows no badge`() {
        assertEquals(NotificationManagerCompat.IMPORTANCE_LOW, PingogramChannel.DailyDigest.importance)
        assertEquals(false, PingogramChannel.DailyDigest.showBadge)
    }

    private companion object {
        const val BIDI_ISOLATES = "⁨⁩"
    }
}
