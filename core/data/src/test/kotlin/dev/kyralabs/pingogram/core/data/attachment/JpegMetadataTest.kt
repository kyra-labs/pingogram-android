package dev.kyralabs.pingogram.core.data.attachment

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class JpegMetadataTest {
    private fun segment(
        marker: Int,
        vararg body: Int,
    ): ByteArray {
        val length = body.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) +
            body.map { it.toByte() }.toByteArray()
    }

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val jfif = segment(0xE0, 0x4A, 0x46, 0x49, 0x46, 0x00)
    private val exif = segment(0xE1, 0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x47, 0x50, 0x53)
    private val xmp = segment(0xE1, 0x68, 0x74, 0x74, 0x70)
    private val icc = segment(0xE2, 0x49, 0x43, 0x43)
    private val comment = segment(0xFE, 0x68, 0x69)
    private val tables = segment(0xDB, 0x00, 0x01, 0x02)
    private val frame = segment(0xC0, 0x08, 0x00, 0x10)
    private val scan = segment(0xDA, 0x01, 0x01, 0x00)

    /** Entropy-coded data with a stuffed 0xFF 0x00, a restart marker, then the end. */
    private val data =
        byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD0.toByte(), 0x56) +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun stripped(jpeg: ByteArray): ByteArray =
        ByteArrayOutputStream().also { JpegMetadata.strip(ByteArrayInputStream(jpeg), it) }.toByteArray()

    @Test
    fun `drops EXIF, XMP, ICC and comments, and keeps JFIF, the tables, the frame and the image data byte for byte`() {
        val jpeg = soi + jfif + exif + xmp + icc + comment + tables + frame + scan + data

        assertArrayEquals(soi + jfif + tables + frame + scan + data, stripped(jpeg))
    }

    @Test
    fun `fill bytes before a marker are passed over`() {
        val jpeg = soi + byteArrayOf(0xFF.toByte()) + exif + tables + scan + data

        assertArrayEquals(soi + tables + scan + data, stripped(jpeg))
    }

    @Test
    fun `tells a JPEG by its first bytes, and refuses anything else`() {
        assertTrue(JpegMetadata.isJpeg(soi + byteArrayOf(0xFF.toByte())))
        assertFalse(JpegMetadata.isJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4E)))
        assertThrows<IOException> { stripped(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }
    }
}
