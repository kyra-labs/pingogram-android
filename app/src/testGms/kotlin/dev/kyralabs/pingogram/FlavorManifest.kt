package dev.kyralabs.pingogram

import android.Manifest

/**
 * What the gms flavor adds to the app's permissions: no "Keep connected", only Firebase Cloud Messaging's WAKE_LOCK on
 * every version, held while it hands a push to the app (approved as "the gms flavor's FCM permissions",
 * docs/adr/0004-push-delivery.md).
 */
internal object FlavorManifest {
    /** Requested on Robolectric's Android 14, beyond the main manifest's. */
    val requestedPermissions = setOf(Manifest.permission.WAKE_LOCK)

    /** Declared, beyond the main manifest's, with their `maxSdkVersion` ("" for none); replaces the main entries. */
    val declaredPermissions = mapOf(Manifest.permission.WAKE_LOCK to "")
}
