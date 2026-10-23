package dev.kyralabs.pingogram.feature.scanner

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Reading QR codes on the device: generated codes, drawn as the camera and a picked image would show them. */
class QrDecoderTest {
    private val decoder = QrDecoder()

    /** [text] as a QR code, [scale] pixels per module, with a quiet zone of four modules; dark modules are 0. */
    private fun luminance(
        text: String,
        scale: Int = 4,
        inverted: Boolean = false,
    ): Triple<ByteArray, Int, Int> {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name())
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M, hints).matrix
        val side = (matrix.width + 2 * QUIET_ZONE) * scale
        val pixels =
            ByteArray(side * side) { index ->
                val x = index % side / scale - QUIET_ZONE
                val y = index / side / scale - QUIET_ZONE
                val dark = x in 0 until matrix.width && y in 0 until matrix.height && matrix.get(x, y).toInt() == 1
                (if (dark != inverted) DARK else LIGHT).toByte()
            }
        return Triple(pixels, side, side)
    }

    @Test
    fun `reads a login link from a camera frame`() {
        val (pixels, width, height) = luminance(LOGIN_LINK)

        assertEquals(LOGIN_LINK, decoder.decodeLuminance(pixels, width, width, height))
    }

    @Test
    fun `reads a frame whose rows are padded`() {
        val (pixels, width, height) = luminance(LINK)
        val stride = width + PADDING
        val padded = ByteArray(stride * height) { LIGHT.toByte() }
        for (row in 0 until height) pixels.copyInto(padded, row * stride, row * width, (row + 1) * width)

        assertEquals(LINK, decoder.decodeLuminance(padded, stride, width, height))
    }

    @Test
    fun `reads a code shown light on dark`() {
        val (pixels, width, height) = luminance(LINK, inverted = true)

        assertEquals(LINK, decoder.decodeLuminance(pixels, width, width, height))
    }

    @Test
    fun `reads a picked image's colors`() {
        val (luminance, width, height) = luminance(LINK)
        val argb = IntArray(luminance.size) { index -> if (luminance[index] == 0.toByte()) BLACK else WHITE }

        assertEquals(LINK, decoder.decodeArgb(argb, width, height))
    }

    @Test
    fun `reads text in other scripts`() {
        val text = "Привет, 世界"
        val (pixels, width, height) = luminance(text)

        assertEquals(text, decoder.decodeLuminance(pixels, width, width, height))
    }

    @Test
    fun `finds nothing in an empty frame`() {
        val side = 200

        assertNull(decoder.decodeLuminance(ByteArray(side * side) { LIGHT.toByte() }, side, side, side))
    }

    @Test
    fun `large images are read at a power of two below 1600 pixels`() {
        assertEquals(1, sampleSize(1600, 1200))
        assertEquals(2, sampleSize(3200, 2400))
        assertEquals(4, sampleSize(4000, 3000))
    }

    private companion object {
        const val LOGIN_LINK = "tg://login?token=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
        const val LINK = "https://t.me/pingogram"
        const val QUIET_ZONE = 4
        const val PADDING = 16
        const val DARK = 0
        const val LIGHT = 255
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
