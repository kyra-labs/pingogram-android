package dev.kyralabs.pingogram.feature.scanner

import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * The camera of the QR code scanner (`docs/adr/0012-camerax-and-zxing-qr-scanner.md`): CameraX's [Preview] drawn by
 * camera-compose's [CameraXViewfinder], filling [modifier]'s bounds, and an [ImageAnalysis] of the latest frames, about
 * 720p, read by [QrAnalyzer] on a background thread of its own while [scanning]. A code found goes to [onFound] on the
 * main thread, once, until [scanning] turns true again. The back camera where there is one, else the front one; the
 * torch follows [torchOn] where the camera has one, which [onTorchAvailable] reports. A camera that cannot be opened
 * (in use by another app, removed, refused by a policy) calls [onUnavailable].
 *
 * Bound to the screen's lifecycle: the camera runs only while the screen is started, and is released when it leaves.
 */
@Composable
internal fun ScannerCamera(
    scanning: Boolean,
    torchOn: Boolean,
    onFound: (String) -> Unit,
    onTorchAvailable: (Boolean) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFound by rememberUpdatedState(onFound)
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analyzer =
        remember(mainExecutor) {
            QrAnalyzer(onFound = { text ->
                mainExecutor.execute { currentOnFound(text) }
            })
        }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    LaunchedEffect(scanning, analyzer) { if (scanning) analyzer.resume() else analyzer.pause() }
    LaunchedEffect(lifecycleOwner, analyzer) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider { request -> surfaceRequest = request } }
        val executor = Executors.newSingleThreadExecutor()
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ANALYSIS_RESOLUTION)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
        try {
            val selector =
                if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            camera = bound
            currentOnTorchAvailable(bound.cameraInfo.hasFlashUnit())
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // No camera matches, or the use cases do not fit it.
            currentOnUnavailable()
        } catch (_: IllegalStateException) {
            // The camera is closed or already in use.
            currentOnUnavailable()
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
            camera = null
            surfaceRequest = null
        }
    }
    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }
    surfaceRequest?.let { request -> CameraXViewfinder(surfaceRequest = request, modifier = modifier.fillMaxSize()) }
}

/** Frames of about 720p for the analysis: enough for codes across a room, little work per frame. */
private val ANALYSIS_RESOLUTION =
    ResolutionSelector
        .Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        ).build()

private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720
