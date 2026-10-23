package dev.kyralabs.pingogram.core.ui.qr

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

class QrCodeTest {
    private val link = "tg://login?token=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"

    @Test
    fun `the QR code decodes back to the login link`() {
        val matrix = checkNotNull(encodeQrCode(link))

        val bits = BitMatrix(matrix.size)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) bits.set(x, y)
            }
        }

        assertEquals(link, Decoder().decode(bits).text)
    }

    @Test
    fun `a login link fits a small QR code with large modules`() {
        // Version 4 (33 modules) holds a 60-character link at error correction level M.
        assertEquals(VERSION_4_SIZE, checkNotNull(encodeQrCode(link)).size)
    }

    @Test
    fun `modules are dark on a light ground with high contrast in light and dark themes`() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { scheme ->
            val (module, background) = qrColors(scheme)

            assertTrue(module.luminance() < background.luminance(), "dark modules in ${scheme.name}")
            assertTrue(contrast(module, background) >= MIN_CONTRAST, "contrast in ${scheme.name}")
        }
    }

    private val ColorScheme.name get() = if (surface.luminance() > HALF) "light" else "dark"

    /** WCAG contrast ratio. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val lighter = max(a.luminance(), b.luminance())
        val darker = min(a.luminance(), b.luminance())
        return (lighter + WCAG_OFFSET) / (darker + WCAG_OFFSET)
    }

    private companion object {
        const val VERSION_4_SIZE = 33
        const val MIN_CONTRAST = 7f
        const val WCAG_OFFSET = 0.05f
        const val HALF = 0.5f
    }
}
