package dev.kyralabs.pingogram.core.data.attachment

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A JPEG's metadata taken out without decoding its pixels: what [strip] writes keeps the image data byte for byte (the
 * frame, its tables and the scan) and drops every application segment but JFIF's (APP1–APP15: EXIF with its location
 * and camera, XMP, ICC profiles, IPTC, makers' own) and every comment. Its orientation goes with EXIF; the caller
 * writes it back alone.
 */
internal object JpegMetadata {
    /** Whether [header], a file's first bytes, starts a JPEG. */
    fun isJpeg(header: ByteArray): Boolean = header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }

    /** Copies the JPEG in [input] to [output] without its metadata; throws [IOException] when it is no JPEG. */
    fun strip(
        input: InputStream,
        output: OutputStream,
    ) {
        val data = DataInputStream(input.buffered())
        if (data.readUnsignedByte() != MARKER || data.readUnsignedByte() != SOI) throw IOException("Not a JPEG")
        output.write(byteArrayOf(MARKER.toByte(), SOI.toByte()))
        while (true) {
            val marker = nextMarker(data)
            if (marker == EOI || marker in STANDALONE) {
                output.write(byteArrayOf(MARKER.toByte(), marker.toByte()))
                if (marker == EOI) return
                continue
            }
            val length = data.readUnsignedShort()
            if (length < 2) throw IOException("Bad segment length")
            val body = ByteArray(length - 2).also(data::readFully)
            if (!isMetadata(marker)) {
                output.write(
                    byteArrayOf(
                        MARKER.toByte(),
                        marker.toByte(),
                        (length shr Byte.SIZE_BITS).toByte(),
                        length.toByte(),
                    ),
                )
                output.write(body)
            }
            if (marker == SOS) {
                // The entropy-coded data and everything after it, as it is.
                data.copyTo(output, DEFAULT_BUFFER_SIZE)
                return
            }
        }
    }

    /** The next marker's code, past the fill bytes before it. */
    private fun nextMarker(data: DataInputStream): Int {
        if (data.readUnsignedByte() != MARKER) throw IOException("Expected a marker")
        var code = data.readUnsignedByte()
        while (code == MARKER) code = data.readUnsignedByte()
        return code
    }

    /** APP1–APP15 and COM; APP0 (JFIF) stays. */
    private fun isMetadata(marker: Int): Boolean = marker == COM || marker in (APP0 + 1)..APP15

    private val MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private const val MARKER = 0xFF
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val COM = 0xFE
    private const val APP0 = 0xE0
    private const val APP15 = 0xEF

    /** Markers without a length: TEM and the restart markers. */
    private val STANDALONE = setOf(0x01) + (0xD0..0xD7)
}
