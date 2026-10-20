package dev.kyralabs.pingogram

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The merged manifest requests exactly the platform permissions the owner approved (CLAUDE.md). Without
 * INTERNET, TDLib cannot open a socket and the app stays on "Connecting…" forever. WRITE_EXTERNAL_STORAGE saves
 * media into the gallery on Android 8–9 only (approved 2026-10-18): later versions save through MediaStore without it,
 * so it is declared up to API 28 and not requested on newer runtimes. The FileProvider that hands TDLib's files to
 * other apps (approved the same day) is private and grants single URIs. POST_NOTIFICATIONS shows notifications
 * (approved 2026-10-19); USE_BIOMETRIC unlocks the passcode lock with the system's biometric prompt (approved
 * 2026-10-20); WorkManager's WAKE_LOCK and FOREGROUND_SERVICE run notification actions as a foreground
 * service before Android 12 only, so they stop at API 30, and its ACCESS_NETWORK_STATE and RECEIVE_BOOT_COMPLETED are
 * left out.
 * WorkManager starts on demand, not through App Startup. The launcher icon is adaptive, with the monochrome layer of
 * themed icons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppManifestTest {
    @Test
    fun `requests exactly the approved platform permissions`() {
        val context = RuntimeEnvironment.getApplication()
        val requested =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .filter { it.startsWith("android.permission.") }
                .toSet()

        // Robolectric runs Android 14, where storage access up to API 28 and WorkManager's permissions up to API 30 are
        // not requested.
        assertEquals(
            setOf(
                Manifest.permission.INTERNET,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.USE_BIOMETRIC,
            ),
            requested,
        )
    }

    @Test
    fun `declares the approved permissions, storage for Android 8 and 9 only`() {
        val declared =
            mergedManifest()
                .elements("uses-permission")
                .map { it.androidAttribute("name") to it.androidAttribute("maxSdkVersion") }
                .filter { (name, _) -> name.startsWith("android.permission.") && name !in TEST_ONLY_PERMISSIONS }
                .toMap()

        assertEquals(
            mapOf(
                Manifest.permission.INTERNET to "",
                Manifest.permission.POST_NOTIFICATIONS to "",
                Manifest.permission.USE_BIOMETRIC to "",
                Manifest.permission.WAKE_LOCK to "30",
                Manifest.permission.FOREGROUND_SERVICE to "30",
                Manifest.permission.WRITE_EXTERNAL_STORAGE to "28",
            ),
            declared,
        )
    }

    @Test
    fun `WorkManager starts on demand, and notification actions reach only the app's own receiver`() {
        val manifest = mergedManifest()
        val initializers =
            manifest
                .elements("meta-data")
                .map { it.androidAttribute("name") }
        val receiver =
            manifest
                .elements("receiver")
                .single { it.androidAttribute("name").endsWith(".NotificationActionReceiver") }

        assertFalse("androidx.work.WorkManagerInitializer" in initializers)
        assertEquals("false", receiver.androidAttribute("exported"))
    }

    @Test
    fun `the file provider is private, grants single URIs and serves the TDLib file paths`() {
        val context = RuntimeEnvironment.getApplication()
        val provider =
            checkNotNull(context.packageManager.resolveContentProvider("${context.packageName}.files", GET_META_DATA))

        assertEquals("androidx.core.content.FileProvider", provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals(R.xml.file_paths, provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))
    }

    @Test
    fun `the launcher icon is adaptive, round too, with a monochrome layer for themed icons`() {
        val context = RuntimeEnvironment.getApplication()
        val application = mergedManifest().elements("application").single()

        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        assertEquals("@mipmap/ic_launcher_round", application.androidAttribute("roundIcon"))
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = context.getDrawable(icon) as AdaptiveIconDrawable
            assertNotNull(drawable.foreground)
            assertNotNull(drawable.monochrome)
        }
    }

    /** The merged manifest Robolectric runs, as the Android Gradle plugin's test config names it. */
    private fun mergedManifest(): Element {
        val config = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties").use(config::load)
        val file = File(config.getProperty("android_merged_manifest"))
        return DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        /** What the test libraries (androidx.test's activity scenarios) add to the unit tests' manifest only. */
        val TEST_ONLY_PERMISSIONS = setOf("android.permission.REORDER_TASKS")
        const val GET_META_DATA = PackageManager.GET_META_DATA
    }
}
