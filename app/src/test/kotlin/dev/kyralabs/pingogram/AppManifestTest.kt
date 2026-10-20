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

/**
 * The merged manifest requests exactly the platform permissions the owner approved (CLAUDE.md). Without
 * INTERNET, TDLib cannot open a socket and the app stays on "Connecting…" forever. WRITE_EXTERNAL_STORAGE saves
 * media into the gallery on Android 8–9 only (approved 2026-10-18): later versions save through MediaStore without it,
 * so it is declared up to API 28 and not requested on newer runtimes. The FileProvider that hands TDLib's files to
 * other apps (approved the same day) is private and grants single URIs. POST_NOTIFICATIONS shows notifications
 * (approved 2026-10-19); USE_BIOMETRIC unlocks the passcode lock with the system's biometric prompt (approved
 * 2026-10-20); WorkManager's WAKE_LOCK and FOREGROUND_SERVICE run notification actions as a foreground
 * service before Android 12 only, so they stop at API 30, and its ACCESS_NETWORK_STATE and RECEIVE_BOOT_COMPLETED are
 * left out, except in the foss flavor, whose "Keep connected" service needs FOREGROUND_SERVICE on every version, its
 * type's FOREGROUND_SERVICE_SPECIAL_USE and RECEIVE_BOOT_COMPLETED (approved 2026-10-19; [FlavorManifest] in each
 * flavor's test sources, `KeepConnectedManifestTest` for the service and its receiver), and the gms flavor, whose
 * Firebase Cloud Messaging keeps WAKE_LOCK on every version and adds its c2dm RECEIVE permission
 * (docs/adr/0004-push-delivery.md, `FirebaseMessagingManifestTest`; foss: `GoogleFreeManifestTest`).
 * WorkManager starts on demand, not through App Startup. The launcher icon is adaptive, with the monochrome layer of
 * themed icons. The locale config lists the shipped languages for Android 13+'s per-app language settings.
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
            ) + FlavorManifest.requestedPermissions,
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
                Manifest.permission.WRITE_EXTERNAL_STORAGE to "28",
            ) + FlavorManifest.declaredPermissions,
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

    @Test
    fun `Android 13 and later list the languages Pingogram ships in its language settings`() {
        val application = mergedManifest().elements("application").single()

        assertEquals("@xml/locales_config", application.androidAttribute("localeConfig"))
    }

    private companion object {
        /** What the test libraries (androidx.test's activity scenarios) add to the unit tests' manifest only. */
        val TEST_ONLY_PERMISSIONS = setOf("android.permission.REORDER_TASKS")
        const val GET_META_DATA = PackageManager.GET_META_DATA
    }
}
