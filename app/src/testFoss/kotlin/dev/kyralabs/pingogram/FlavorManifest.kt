package dev.kyralabs.pingogram

import android.Manifest

/**
 * What the foss flavor adds to the app's permissions for "Keep connected" (approved by the owner on 2026-10-19): the
 * service's type `specialUse` (Android 14+). FOREGROUND_SERVICE (transfers) and RECEIVE_BOOT_COMPLETED, for its start
 * after a reboot, come from the main manifest.
 */
internal object FlavorManifest {
    /** Requested on Robolectric's Android 14, beyond the main manifest's. */
    val requestedPermissions =
        setOf(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
        )

    /** Declared, beyond the main manifest's, with their `maxSdkVersion` ("" for none). */
    val declaredPermissions =
        mapOf(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE to "",
        )
}
