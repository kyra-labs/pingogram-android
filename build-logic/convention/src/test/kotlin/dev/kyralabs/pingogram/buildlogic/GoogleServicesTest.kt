package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleServicesTest {
    @Test
    fun `the file in the gms source set configures Firebase`() {
        assertEquals(GoogleServices.Setup.Configured, GoogleServices.resolve(flavorFile = true, misplacedFile = false))
    }

    @Test
    fun `without the file, packaging gms fails with a message naming the file and the setup guide`() {
        val missing =
            assertInstanceOf(
                GoogleServices.Setup.Missing::class.java,
                GoogleServices.resolve(flavorFile = false, misplacedFile = false),
            )

        assertTrue("app/src/gms/google-services.json" in missing.message, missing.message)
        assertTrue("docs/OWNER_SETUP.md" in missing.message, missing.message)
        assertTrue("GOOGLE_SERVICES_JSON_BASE64" in missing.message, missing.message)
    }

    @Test
    fun `a file in app itself fails, as it would configure Firebase for foss too`() {
        for (flavorFile in listOf(true, false)) {
            val misplaced =
                assertInstanceOf(
                    GoogleServices.Setup.Misplaced::class.java,
                    GoogleServices.resolve(flavorFile = flavorFile, misplacedFile = true),
                )

            assertTrue("move it to app/src/gms/google-services.json" in misplaced.message, misplaced.message)
        }
    }
}

class GoogleFreeTest {
    @Test
    fun `Firebase, Play services, data transport and Play Core modules are violations, in their order`() {
        val modules =
            listOf(
                "androidx.core:core:1.18.0",
                "com.google.android.datatransport:transport-runtime:5.0.0",
                "com.google.android.gms:play-services-base:18.9.0",
                "com.google.android.play:core-common:2.0.4",
                "com.google.firebase:firebase-bom:35.0.2",
                "com.google.firebase:firebase-messaging:26.0.0",
            )

        assertEquals(modules.drop(1), GoogleFree.violations(modules))
    }

    @Test
    fun `open-source Google libraries and look-alike groups are fine`() {
        val modules =
            listOf(
                "com.google.dagger:hilt-android:2.60.1",
                "com.google.guava:guava:33.3.1-android",
                "com.google.zxing:core:3.5.4",
                "com.google.firebasefoo:lib:1.0",
            )

        assertEquals(emptyList<String>(), GoogleFree.violations(modules))
    }

    @Test
    fun `the message names the variant, the modules and how to find their origin`() {
        val message = GoogleFree.message("fossRelease", listOf("com.google.firebase:firebase-messaging:26.0.0"))

        assertTrue("fossRelease" in message, message)
        assertTrue("com.google.firebase:firebase-messaging:26.0.0" in message, message)
        assertTrue("--configuration fossReleaseRuntimeClasspath" in message, message)
    }
}
