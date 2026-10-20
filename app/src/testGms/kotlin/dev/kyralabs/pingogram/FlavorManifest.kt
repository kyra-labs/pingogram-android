package dev.kyralabs.pingogram

import android.Manifest

/**
 * What the gms flavor adds to the app's permissions: no "Keep connected", so only WorkManager's FOREGROUND_SERVICE, up
 * to API 30, for expedited work before Android 12; and Firebase Cloud Messaging's WAKE_LOCK on every version, held
 * while it hands a push to the app (approved as "the gms flavor's FCM permissions", docs/adr/0004-push-delivery.md).
 */
internal object FlavorManifest {
    /** Requested on Robolectric's Android 14, beyond the main manifest's. */
    val requestedPermissions = setOf(Manifest.permission.WAKE_LOCK)

    /** Declared, beyond the main manifest's, with their `maxSdkVersion` ("" for none); replaces the main entries. */
    val declaredPermissions =
        mapOf(
            Manifest.permission.FOREGROUND_SERVICE to "30",
            Manifest.permission.WAKE_LOCK to "",
        )
}
