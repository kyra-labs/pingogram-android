package dev.kyralabs.pingogram

import android.Manifest

/**
 * What the foss flavor adds to the app's permissions for "Keep connected" (approved by the owner on 2026-10-19): the
 * service's type `specialUse` (Android 14+) and its start after a reboot. FOREGROUND_SERVICE comes from the main
 * manifest (transfers).
 */
internal object FlavorManifest {
    /** Requested on Robolectric's Android 14, beyond the main manifest's. */
    val requestedPermissions =
        setOf(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
        )

    /** Declared, beyond the main manifest's, with their `maxSdkVersion` ("" for none). */
    val declaredPermissions =
        mapOf(
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE to "",
            Manifest.permission.RECEIVE_BOOT_COMPLETED to "",
        )
}
