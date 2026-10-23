package dev.kyralabs.pingogram.feature.calls.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the device has a camera and the app may use it (`CAMERA`), which video calls need
 * (`docs/adr/0019-video-and-group-calls-scope.md`); a seam for the call screen's tests.
 */
interface CameraAccess {
    fun hasCamera(): Boolean

    fun isGranted(): Boolean
}

internal class AndroidCameraAccess
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CameraAccess {
        override fun hasCamera(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
