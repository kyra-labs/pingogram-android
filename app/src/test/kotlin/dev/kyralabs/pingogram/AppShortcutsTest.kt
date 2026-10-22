package dev.kyralabs.pingogram

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.res.XmlResourceParser
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.net.toUri
import dev.kyralabs.pingogram.link.linkOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The static app shortcuts (`res/xml/shortcuts.xml`): New chat, Search and Saved Messages, each a Telegram link that
 * the main activity of this very build opens as any other link (`docs/adr/0013-deep-links.md`), with an adaptive icon
 * and labels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppShortcutsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val shortcuts = context.resources.getXml(R.xml.shortcuts).shortcuts()

    @Test
    fun `offers New chat, Search and Saved Messages as Telegram links`() {
        assertEquals(
            mapOf(
                "new_chat" to "tg://new",
                "search" to "tg://chats/search",
                "saved_messages" to "tg://settings/saved-messages",
            ),
            shortcuts.associate { it.id to it.intent.dataString },
        )
    }

    @Test
    fun `each opens its link in the main activity of this build, debug builds' suffix included`() {
        shortcuts.forEach { shortcut ->
            assertEquals(ComponentName(context.packageName, MainActivity::class.java.name), shortcut.intent.component)
            assertEquals(shortcut.intent.dataString, linkOf(shortcut.intent))
        }
    }

    @Test
    fun `each has an adaptive icon and labels`() {
        shortcuts.forEach { shortcut ->
            assertTrue(context.getDrawable(shortcut.icon) is AdaptiveIconDrawable)
            assertTrue(context.getString(shortcut.shortLabel).isNotBlank())
            assertTrue(context.getString(shortcut.longLabel).isNotBlank())
        }
    }

    /** One `<shortcut>`: its id, the resources of its icon and labels, and its intent. */
    private class Shortcut(
        val id: String,
        val icon: Int,
        val shortLabel: Int,
        val longLabel: Int,
        val intent: Intent,
    )

    private fun XmlResourceParser.shortcuts(): List<Shortcut> {
        val shortcuts = mutableListOf<Shortcut>()
        var id: String? = null
        var resources = IntArray(0)
        while (next() != XmlPullParser.END_DOCUMENT) {
            if (eventType != XmlPullParser.START_TAG) continue
            when (name) {
                "shortcut" -> {
                    id = getAttributeValue(ANDROID, "shortcutId")
                    resources =
                        intArrayOf(
                            getAttributeResourceValue(ANDROID, "icon", 0),
                            getAttributeResourceValue(ANDROID, "shortcutShortLabel", 0),
                            getAttributeResourceValue(ANDROID, "shortcutLongLabel", 0),
                        )
                }

                "intent" -> {
                    val packageName = context.getString(getAttributeResourceValue(ANDROID, "targetPackage", 0))
                    val intent =
                        Intent(getAttributeValue(ANDROID, "action"), getAttributeValue(ANDROID, "data").toUri())
                            .setClassName(packageName, getAttributeValue(ANDROID, "targetClass"))
                    shortcuts += Shortcut(checkNotNull(id), resources[0], resources[1], resources[2], intent)
                }
            }
        }
        return shortcuts
    }
}

private const val ANDROID = "http://schemas.android.com/apk/res/android"
