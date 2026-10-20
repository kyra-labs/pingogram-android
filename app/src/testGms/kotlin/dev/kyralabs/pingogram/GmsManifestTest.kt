package dev.kyralabs.pingogram

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The gms flavor ships none of the foss flavor's "Keep connected": no service of its own, no boot receiver. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GmsManifestTest {
    @Test
    fun `declares no keep connected service and no boot receiver`() {
        val manifest = mergedManifest()
        val components =
            (manifest.elements("service") + manifest.elements("receiver")).map { it.androidAttribute("name") }

        assertEquals(emptyList<String>(), components.filter { it.startsWith("dev.kyralabs.pingogram.background.") })
    }
}
