package dev.kyralabs.pingogram.link

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which intents carry a Telegram link to open. A plain [Application], like the app's other tests: Pingogram's own would
 * start TDLib and its controllers, whose coroutines outlive the test and fail the next one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LinkIntentsTest {
    @Test
    fun `a view intent of a Telegram link carries it`() {
        assertEquals("https://t.me/durov", linkOf(view("https://t.me/durov")))
        assertEquals("tg://resolve?domain=durov", linkOf(view("tg://resolve?domain=durov")))
    }

    @Test
    fun `other links are none`() {
        assertNull(linkOf(view("https://example.com/t.me")))
        assertNull(linkOf(view("content://media/1")))
    }

    @Test
    fun `other intents are none`() {
        assertNull(linkOf(null))
        assertNull(linkOf(Intent(Intent.ACTION_MAIN)))
        assertNull(linkOf(Intent(Intent.ACTION_SEND, Uri.parse("https://t.me/durov"))))
        assertNull(linkOf(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `reopening the app from the recent apps opens the link no more`() {
        assertNull(linkOf(view("https://t.me/durov").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
}
