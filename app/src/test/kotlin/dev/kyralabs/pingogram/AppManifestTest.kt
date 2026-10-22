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
 * 2026-10-20); RECORD_AUDIO records voice messages (approved 2026-10-20), without requiring a microphone
 * of the device; ACCESS_NETWORK_STATE tells Wi-Fi, mobile data and roaming apart for automatic download and TDLib
 * (approved 2026-10-20); WorkManager's WAKE_LOCK runs notification actions as a foreground service before Android 12
 * only, so it stops at API 30; FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC run the transfers the user started
 * in WorkManager's foreground service of type dataSync (approved 2026-10-20), and POST_PROMOTED_NOTIFICATIONS lets
 * Android 16 show their progress as a Live Update (approved 2026-10-21); WorkManager's RECEIVE_BOOT_COMPLETED is
 * left out, except in the foss flavor, whose "Keep connected" service needs its
 * type's FOREGROUND_SERVICE_SPECIAL_USE and RECEIVE_BOOT_COMPLETED (approved 2026-10-19; [FlavorManifest] in each
 * flavor's test sources, `KeepConnectedManifestTest` for the service and its receiver), and the gms flavor, whose
 * Firebase Cloud Messaging keeps WAKE_LOCK on every version and adds its c2dm RECEIVE permission
 * (docs/adr/0004-push-delivery.md, `FirebaseMessagingManifestTest`; foss: `GoogleFreeManifestTest`).
 * ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION send the device's location from the attach menu (approved
 * 2026-10-20), in the foreground only: no background location, and location hardware is optional.
 * WorkManager starts on demand, not through App Startup. The launcher icon is adaptive, with the monochrome layer of
 * themed icons. The locale config lists the shipped languages for Android 13+'s per-app language settings.
 * Telegram links (`tg:`, `t.me`, `telegram.me`, `telegram.dog`) open in the single-task main activity through browsable
 * view filters that are not App Links verified (docs/adr/0013-deep-links.md): Telegram owns those domains.
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
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.USE_BIOMETRIC,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                Manifest.permission.POST_PROMOTED_NOTIFICATIONS,
                Manifest.permission.ACCESS_NETWORK_STATE,
                // Contacts sync only, opt-in (docs/adr/0011-contacts-sync.md): read when the user turns it on, opens
                // Contacts while it is on, or taps Sync now; never in the background.
                Manifest.permission.READ_CONTACTS,
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
                Manifest.permission.ACCESS_COARSE_LOCATION to "",
                Manifest.permission.ACCESS_FINE_LOCATION to "",
                Manifest.permission.USE_BIOMETRIC to "",
                Manifest.permission.RECORD_AUDIO to "",
                Manifest.permission.FOREGROUND_SERVICE to "",
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC to "",
                Manifest.permission.POST_PROMOTED_NOTIFICATIONS to "",
                Manifest.permission.ACCESS_NETWORK_STATE to "",
                // Contacts sync's only permission, on every version.
                Manifest.permission.READ_CONTACTS to "",
                Manifest.permission.WAKE_LOCK to "30",
                Manifest.permission.WRITE_EXTERNAL_STORAGE to "28",
            ) + FlavorManifest.declaredPermissions,
            declared,
        )
    }

    @Test
    fun `location is foreground only, and its hardware is optional`() {
        val manifest = mergedManifest()
        val declared = manifest.elements("uses-permission").map { it.androidAttribute("name") }
        val features =
            manifest
                .elements("uses-feature")
                .associate { it.androidAttribute("name") to it.androidAttribute("required") }

        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in declared)
        assertEquals(
            mapOf(
                "android.hardware.location" to "false",
                "android.hardware.location.gps" to "false",
                "android.hardware.location.network" to "false",
            ),
            features.filterKeys { it.startsWith("android.hardware.location") },
        )
    }

    @Test
    fun `the microphone is used where there is one, not required to install`() {
        val microphone =
            mergedManifest()
                .elements("uses-feature")
                .single { it.androidAttribute("name") == "android.hardware.microphone" }

        assertEquals("false", microphone.androidAttribute("required"))
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
    fun `transfers run in WorkManager's foreground service of type dataSync, cancelled only by the app`() {
        val manifest = mergedManifest()
        val service =
            manifest
                .elements("service")
                .single { it.androidAttribute("name") == "androidx.work.impl.foreground.SystemForegroundService" }
        val receiver =
            manifest
                .elements("receiver")
                .single { it.androidAttribute("name").endsWith(".TransferCancelReceiver") }

        assertEquals("dataSync", service.androidAttribute("foregroundServiceType"))
        assertEquals("false", service.androidAttribute("exported"))
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

    @Test
    fun `Telegram links open in the main activity, without App Links verification`() {
        val activity =
            mergedManifest()
                .elements("activity")
                .single { it.androidAttribute("name") == "dev.kyralabs.pingogram.MainActivity" }
        val views =
            activity.elements("intent-filter").filter { filter ->
                filter.elements("action").any { it.androidAttribute("name") == "android.intent.action.VIEW" }
            }
        val filters =
            views.map { filter ->
                val categories = filter.elements("category").map { it.androidAttribute("name") }.toSet()
                val data = filter.elements("data")
                Triple(
                    categories,
                    data.map { it.androidAttribute("scheme") }.filter { it.isNotEmpty() }.toSet(),
                    data.map { it.androidAttribute("host") }.filter { it.isNotEmpty() }.toSet(),
                )
            }
        val browsable = setOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE")

        assertEquals("singleTask", activity.androidAttribute("launchMode"))
        assertEquals(
            setOf(
                Triple(browsable, setOf("tg"), emptySet()),
                Triple(browsable, setOf("https", "http"), setOf("t.me", "*.t.me", "telegram.me", "telegram.dog")),
            ),
            filters.toSet(),
        )
        // Said explicitly, as Lint asks of browsable http(s) filters that are not App Links.
        assertEquals(listOf("", "false"), views.map { it.androidAttribute("autoVerify") }.sorted())
    }

    private companion object {
        /** What the test libraries (androidx.test's activity scenarios) add to the unit tests' manifest only. */
        val TEST_ONLY_PERMISSIONS = setOf("android.permission.REORDER_TASKS")
        const val GET_META_DATA = PackageManager.GET_META_DATA
    }
}
