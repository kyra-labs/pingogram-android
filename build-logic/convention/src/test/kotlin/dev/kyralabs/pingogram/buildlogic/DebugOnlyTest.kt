package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugOnlyTest {
    @Test
    fun `LeakCanary's modules are debug-only, and nothing else is`() {
        val modules =
            listOf(
                "androidx.core:core:1.17.0",
                "com.squareup.leakcanary:leakcanary-android:2.14",
                "com.squareup.leakcanary:shark:2.14",
                "com.squareup.okio:okio:3.9.0",
            )

        assertEquals(
            listOf("com.squareup.leakcanary:leakcanary-android:2.14", "com.squareup.leakcanary:shark:2.14"),
            DebugOnly.violations(modules),
        )
    }

    @Test
    fun `the message names the variant, the modules and the fix`() {
        val message = DebugOnly.message("fossBenchmarkRelease", listOf("com.squareup.leakcanary:shark:2.14"))

        assertTrue("fossBenchmarkRelease" in message, message)
        assertTrue("com.squareup.leakcanary:shark:2.14" in message, message)
        assertTrue("debugImplementation" in message, message)
        assertTrue("fossBenchmarkReleaseRuntimeClasspath" in message, message)
    }
}
