package dev.kyralabs.pingogram.feature.calls.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.util.concurrent.Executor

/**
 * The demo call's camera (`docs/adr/0034-demo-mode-for-store-review.md`): the platform's camera API, opened only while
 * the user has the camera on in a demo call, its frames turned into the I420 the call screen's renderers take and
 * handed to [sink], the self-view. Nothing else sees them. The call screen asks for the camera's permission before it
 * turns the camera on, as in a real call. Not thread-safe beyond [sink]: called from one call manager at a time.
 */
internal class DemoCamera(
    private val context: Context,
) {
    @Volatile
    var sink: VideoFrameSink? = null

    private var thread: HandlerThread? = null
    private var device: CameraDevice? = null
    private var reader: ImageReader? = null
    private var shown: String? = null

    /** Opens [camera] at about [quality]'s size, or closes the camera with null. */
    fun show(
        camera: CallCamera?,
        quality: VideoQuality,
    ) {
        if (camera?.id == shown) return
        close()
        if (camera == null) return
        // A camera taken away (by another app, or the system) leaves the self-view empty, as in a real call.
        runCatching { open(camera, quality) }.onFailure { Log.w(TAG, "Camera unavailable: ${it.javaClass.simpleName}") }
    }

    // The call screen has the camera's permission before the call turns the camera on (CameraAccess).
    @SuppressLint("MissingPermission")
    private fun open(
        camera: CallCamera,
        quality: VideoQuality,
    ) {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val characteristics = manager.getCameraCharacteristics(camera.id)
        val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val size = sizeFor(characteristics, quality) ?: return
        val handlerThread = HandlerThread(THREAD_NAME).also { it.start() }
        val handler = Handler(handlerThread.looper)
        val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, MAX_IMAGES)
        imageReader.setOnImageAvailableListener(
            { ready ->
                val image = ready.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use { sink?.onFrame(it.toI420(), it.width, it.height, rotation) }
            },
            handler,
        )
        thread = handlerThread
        reader = imageReader
        shown = camera.id
        manager.openCamera(
            camera.id,
            object : CameraDevice.StateCallback() {
                override fun onOpened(opened: CameraDevice) {
                    if (reader !== imageReader) {
                        opened.close()
                        return
                    }
                    device = opened
                    startCapture(opened, imageReader, handler)
                }

                override fun onDisconnected(closed: CameraDevice) = closed.close()

                override fun onError(
                    failed: CameraDevice,
                    error: Int,
                ) = failed.close()
            },
            handler,
        )
    }

    private fun startCapture(
        device: CameraDevice,
        reader: ImageReader,
        handler: Handler,
    ) {
        val callback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    runCatching {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        request.addTarget(reader.surface)
                        session.setRepeatingRequest(request.build(), null, handler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) = session.close()
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val executor = Executor { handler.post(it) }
            val configuration =
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(reader.surface)),
                    executor,
                    callback,
                )
            device.createCaptureSession(configuration)
        } else {
            // Android 8 has only this form, which Android 9 replaced with the one above.
            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(reader.surface), callback, handler)
        }
    }

    private fun close() {
        shown = null
        device?.close()
        device = null
        reader?.close()
        reader = null
        thread?.quitSafely()
        thread = null
    }

    private companion object {
        const val TAG = "DemoCamera"
        const val THREAD_NAME = "DemoCamera"
        const val MAX_IMAGES = 2

        /** The self-view is small: a size near this costs little to convert. */
        const val PREFERRED_SIDE = 960

        /** The largest YUV size within [PREFERRED_SIDE] and [quality], or the smallest the camera has. */
        fun sizeFor(
            characteristics: CameraCharacteristics,
            quality: VideoQuality,
        ): Size? {
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
            val limit = minOf(PREFERRED_SIDE, quality.width)
            return sizes.filter { maxOf(it.width, it.height) <= limit }.maxByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height }
        }
    }
}

/** This YUV_420_888 image as packed I420: the Y plane, then U and V, each without padding. */
private fun Image.toI420(): ByteArray {
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val out = ByteArray(width * height + 2 * chromaWidth * chromaHeight)
    var offset = planes[0].copyTo(out, 0, width, height)
    offset = planes[1].copyTo(out, offset, chromaWidth, chromaHeight)
    planes[2].copyTo(out, offset, chromaWidth, chromaHeight)
    return out
}

/** Copies [width] × [height] samples of this plane into [out] at [offset]; returns the offset after them. */
private fun Image.Plane.copyTo(
    out: ByteArray,
    offset: Int,
    width: Int,
    height: Int,
): Int {
    val data = buffer
    var at = offset
    for (row in 0 until height) {
        val start = row * rowStride
        if (pixelStride == 1) {
            data.position(start)
            data.get(out, at, width)
            at += width
        } else {
            for (column in 0 until width) out[at++] = data.get(start + column * pixelStride)
        }
    }
    return at
}
