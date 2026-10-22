package dev.kyralabs.pingogram.feature.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Finds a QR code in an image and reads its text, on the device with ZXing
 * (`docs/adr/0012-camerax-and-zxing-qr-scanner.md`): QR codes only, their text as UTF-8 unless the code says otherwise.
 * Plain JVM code, no Android, so the camera's frames ([decodeLuminance]) and picked images ([decodeArgb]) are read the
 * same way, and tests read generated codes.
 *
 * One decoder per thread: ZXing's reader keeps state between calls.
 */
internal class QrDecoder {
    private val reader = QRCodeReader()

    /**
     * The text of the QR code in the luminance plane [luminance] of a camera frame ([width] × [height] pixels,
     * [rowStride] bytes per row, the Y plane of YUV_420_888), or null if it holds none. A code seen mirrored or
     * inverted (light on dark) is read as well.
     */
    fun decodeLuminance(
        luminance: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
    ): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return decode(source) ?: decode(source.invert())
    }

    /** The text of the QR code in [pixels], [width] × [height] ARGB colors (a picked image), or null. */
    fun decodeArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        return decode(source) ?: decode(source.invert())
    }

    private fun decode(source: LuminanceSource): String? =
        try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text?.takeIf { it.isNotEmpty() }
        } catch (_: NotFoundException) {
            null
        } catch (_: ReaderException) {
            // A code that was found but could not be read (checksum, format): the next frame may read it.
            null
        } finally {
            reader.reset()
        }

    private companion object {
        val HINTS: Map<DecodeHintType, Any> =
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            )
    }
}
