package dev.kyralabs.pingogram

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The foss flavor's merged manifest has nothing of Firebase or Play services (docs/adr/0004-push-delivery.md; the
 * build's `check<Variant>GoogleFree` keeps their libraries out).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoogleFreeManifestTest {
    private val manifest = mergedManifest()

    @Test
    fun `declares no Firebase or Play services component, permission or setting`() {
        val names =
            listOf("uses-permission", "service", "receiver", "provider", "activity", "meta-data").flatMap { tag ->
                manifest.elements(tag).map { it.androidAttribute("name") }
            }

        assertEquals(emptyList<String>(), names.filter { it.startsWith("com.google.") || it.startsWith("firebase_") })
        assertFalse(names.any { it.endsWith(".PingogramMessagingService") })
    }
}
