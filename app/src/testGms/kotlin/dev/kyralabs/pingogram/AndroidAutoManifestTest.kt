package dev.kyralabs.pingogram

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The gms flavor declares Android Auto's notification use, and nothing else of Android Auto: no car screen
 * (docs/ANDROID_AUTO.md). foss declares no Google name (`GoogleFreeManifestTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidAutoManifestTest {
    @Test
    fun `Android Auto takes the conversation notifications, and nothing else of the app`() {
        val application = mergedManifest().elements("application").single()
        val auto =
            application.elements("meta-data").single {
                it.androidAttribute("name") == "com.google.android.gms.car.application"
            }
        assertEquals("@xml/automotive_app_desc", auto.androidAttribute("resource"))
        val uses = mutableListOf<String>()
        val parser = RuntimeEnvironment.getApplication().resources.getXml(R.xml.automotive_app_desc)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses") {
                uses += parser.getAttributeValue(null, "name")
            }
        }
        assertEquals(listOf("notification"), uses)
    }
}
