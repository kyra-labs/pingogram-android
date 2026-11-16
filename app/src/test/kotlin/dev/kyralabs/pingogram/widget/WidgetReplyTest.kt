package dev.kyralabs.pingogram.widget

import android.app.Application
import android.content.Intent
import dev.kyralabs.pingogram.account.accountOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A Conversation widget's Reply intent ([WidgetReply]), which MainActivity and the account switch read. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetReplyTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `names the chat and its account, which the app switches to first`() {
        val intent = WidgetReply.intent(context, chatId = -100, slot = 1)

        assertEquals(ConversationKey(slot = 1, chatId = -100), WidgetReply.chatOf(intent))
        assertEquals(1, accountOf(intent))
        assertNotEquals(intent.data, WidgetReply.intent(context, chatId = -200, slot = 1).data)
    }

    @Test
    fun `other intents and one reopened from the recent apps name none`() {
        val replayed =
            WidgetReply.intent(context, chatId = -100, slot = 0).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(WidgetReply.chatOf(replayed))
        assertNull(WidgetReply.chatOf(Intent(Intent.ACTION_MAIN)))
        assertNull(WidgetReply.chatOf(Intent(WidgetReply.ACTION_REPLY)))
    }
}
