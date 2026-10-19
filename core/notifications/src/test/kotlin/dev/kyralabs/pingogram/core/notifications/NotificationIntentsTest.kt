package dev.kyralabs.pingogram.core.notifications

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationIntentsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val launcher = context.registerLauncherActivity()

    @Test
    fun `the intent of a notification opens its chat in the launcher activity`() {
        val intent = checkNotNull(NotificationIntents.openChat(context, chatId = -1001234))

        assertEquals(launcher, intent.component)
        assertEquals(-1001234L, NotificationIntents.chatIdOf(intent))
    }

    @Test
    fun `each chat has an intent of its own`() {
        val first = NotificationIntents.openChat(context, chatId = 1)!!
        val second = NotificationIntents.openChat(context, chatId = 2)!!

        assertEquals(false, first.filterEquals(second))
    }

    @Test
    fun `an intent replayed from the recent apps opens nothing`() {
        val intent =
            NotificationIntents
                .openChat(
                    context,
                    chatId = 1,
                )!!
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(NotificationIntents.chatIdOf(intent))
    }

    @Test
    fun `other intents open no chat`() {
        assertNull(NotificationIntents.chatIdOf(null))
        assertNull(NotificationIntents.chatIdOf(context.packageManager.getLaunchIntentForPackage(context.packageName)))
        assertNull(
            NotificationIntents.chatIdOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/chat/1")).putExtra("chat_id", 1L),
            ),
        )
    }
}
