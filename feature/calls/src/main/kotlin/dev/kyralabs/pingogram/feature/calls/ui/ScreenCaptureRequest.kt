package dev.kyralabs.pingogram.feature.calls.ui

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.kyralabs.pingogram.feature.calls.engine.ScreenCaptureConsent

/**
 * Shows Android's consent dialog for capturing the screen (`docs/adr/0026-screen-sharing.md`) when the returned
 * function is called. An agreed result goes to the engine through [consent] before [onResult] hears `true`; a declined
 * one only to [onResult].
 */
@Composable
internal fun rememberScreenCaptureRequest(
    consent: ScreenCaptureConsent,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val granted = result.resultCode == Activity.RESULT_OK && data != null
            if (granted) consent.grant(data)
            onResult(granted)
        }
    return {
        val projection = context.getSystemService(MediaProjectionManager::class.java)
        launcher.launch(projection.createScreenCaptureIntent())
    }
}
