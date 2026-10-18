package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.runtime.Immutable
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** The modules of a QR code, [size] × [size], without its quiet zone. */
@Immutable
internal class QrMatrix(
    val size: Int,
    private val dark: BooleanArray,
) {
    fun isDark(
        x: Int,
        y: Int,
    ): Boolean = dark[y * size + x]
}

/**
 * Encodes [text] as a QR code with error correction level M, on the device with ZXing; null if it does not fit in one
 * (a `tg://login` link always does). An ASCII text is encoded in byte mode without an ECI header, which every
 * scanner reads.
 */
internal fun encodeQrCode(text: String): QrMatrix? {
    val matrix =
        try {
            Encoder.encode(text, ErrorCorrectionLevel.M).matrix
        } catch (_: WriterException) {
            return null
        }
    val size = matrix.width
    return QrMatrix(size, BooleanArray(size * size) { matrix.get(it % size, it / size).toInt() == 1 })
}
