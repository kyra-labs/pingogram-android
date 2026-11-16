package dev.kyralabs.pingogram.widget

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A Conversation widget's "Choose a chat" intent ([ConversationWidgetSetup]), which MainActivity reads. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConversationWidgetSetupTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the intent names its widget, and each widget's intent is its own`() {
        val intent = ConversationWidgetSetup.intent(context, 7)

        assertEquals(7, ConversationWidgetSetup.appWidgetIdOf(intent))
        assertNotEquals(intent.data, ConversationWidgetSetup.intent(context, 8).data)
    }

    @Test
    fun `other intents and an intent without a widget name none`() {
        assertNull(ConversationWidgetSetup.appWidgetIdOf(Intent(Intent.ACTION_MAIN)))
        assertNull(ConversationWidgetSetup.appWidgetIdOf(Intent(ConversationWidgetSetup.ACTION_CHOOSE_CHAT)))
        assertNull(ConversationWidgetSetup.appWidgetIdOf(null))
    }

    @Test
    fun `an id that is no Conversation widget of this app is refused`() {
        val kept = kotlinx.coroutines.runBlocking { ConversationWidgetSetup.choose(context, 99, slot = 0, chatId = 1) }

        assertEquals(false, kept)
    }
}
