package dev.kyralabs.pingogram.feature.calls.engine

import android.content.Intent
import io.github.pytgcalls.devices.JavaVideoCapturerModule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScreenCaptureConsent] for NTgCalls' screen capturer, which reads the consent when the capture starts
 * (`docs/adr/0026-screen-sharing.md`). Since Android 14 a consent is good for one capture: each share asks again.
 */
@Singleton
class NtgScreenCaptureConsent
    @Inject
    constructor() : ScreenCaptureConsent {
        override fun grant(result: Any) {
            JavaVideoCapturerModule.setMediaProjectionPermissionResult(result as Intent)
        }
    }
