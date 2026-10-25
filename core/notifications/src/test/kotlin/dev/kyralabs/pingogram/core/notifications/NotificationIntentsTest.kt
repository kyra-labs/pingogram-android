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

    @Test
    fun `another account's intent names its account and differs from the first account's`() {
        val first = NotificationIntents.openChat(context, chatId = 1)!!
        val second = NotificationIntents.openChat(context, chatId = 1, slot = 2)!!

        assertEquals(0, NotificationIntents.accountOf(first))
        assertEquals(2, NotificationIntents.accountOf(second))
        assertEquals(1L, NotificationIntents.chatIdOf(second))
        assertEquals(false, first.filterEquals(second))
    }

    @Test
    fun `the locked chats' intent names its account`() {
        val intent = NotificationIntents.openLockedChatsPendingIntent(context, slot = 1)

        assertEquals(true, intent != null)
        assertNull(NotificationIntents.accountOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/durov"))))
    }

    @Test
    fun `shortcut ids carry another account's slot, the first account's stay as they were`() {
        assertEquals("chat_-100", NotificationIntents.shortcutId(-100))
        assertEquals("chat_1_-100", NotificationIntents.shortcutId(-100, slot = 1))
        assertEquals(-100L, NotificationIntents.chatIdOfShortcut("chat_1_-100"))
        assertEquals(1, NotificationIntents.accountOfShortcut("chat_1_-100"))
        assertEquals(0, NotificationIntents.accountOfShortcut("chat_42"))
        assertEquals(42L, NotificationIntents.chatIdOfShortcut("chat_42"))
        assertNull(NotificationIntents.chatIdOfShortcut("chat_x_1"))
        assertNull(NotificationIntents.accountOfShortcut("other_1"))
    }
}
