package dev.kyralabs.pingogram.feature.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads QR codes from the camera's frames ([ImageAnalysis], on its own background executor): the luminance plane of a
 * frame at most every [intervalMillis], decoded with [QrDecoder], and the first text found goes to [onFound], once.
 * It then rests until [resume], so a code held in view is not read again while its result shows. Frames in between are
 * closed at once, which keeps the camera's buffer free (`STRATEGY_KEEP_ONLY_LATEST`).
 *
 * Nothing leaves the device: the frame's pixels are read here and dropped; only the text found goes on.
 */
internal class QrAnalyzer(
    private val onFound: (String) -> Unit,
    private val intervalMillis: Long = ANALYSIS_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()
    private val paused = AtomicBoolean(false)
    private var lastAnalysis = 0L
    private var buffer = ByteArray(0)

    /** Reads frames again, after the result of the last code was dismissed. */
    fun resume() {
        paused.set(false)
    }

    /** Stops reading frames, e.g. while another way of scanning (an image) runs. */
    fun pause() {
        paused.set(true)
    }

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            val now = clock()
            if (paused.get() || now - lastAnalysis < intervalMillis) return
            lastAnalysis = now
            val text = read(frame) ?: return
            if (paused.compareAndSet(false, true)) onFound(text)
        }
    }

    /** The text of the QR code in [frame]'s luminance (its first plane, Y), null if it shows none. */
    private fun read(frame: ImageProxy): String? {
        val plane = frame.planes.firstOrNull() ?: return null
        val data = plane.buffer
        data.rewind()
        if (buffer.size != data.remaining()) buffer = ByteArray(data.remaining())
        data.get(buffer)
        return decoder.decodeLuminance(buffer, plane.rowStride, frame.width, frame.height)
    }

    private companion object {
        /** About five reads a second: quick enough to feel instant, light on the battery. */
        const val ANALYSIS_INTERVAL_MILLIS = 200L
    }
}
