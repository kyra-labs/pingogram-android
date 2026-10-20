package dev.kyralabs.pingogram

import android.Manifest

/**
 * What the gms flavor adds to the app's permissions: no "Keep connected", so only WorkManager's FOREGROUND_SERVICE, up
 * to API 30, for expedited work before Android 12.
 */
internal object FlavorManifest {
    /** Requested on Robolectric's Android 14, beyond the main manifest's: nothing. */
    val requestedPermissions = emptySet<String>()

    /** Declared, beyond the main manifest's, with their `maxSdkVersion`. */
    val declaredPermissions = mapOf(Manifest.permission.FOREGROUND_SERVICE to "30")
}
