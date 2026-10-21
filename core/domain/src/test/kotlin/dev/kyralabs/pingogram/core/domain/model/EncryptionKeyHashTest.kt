package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How a secret chat's key hash becomes its 12 × 12 image and its hexadecimal pairs (td_api.tl `secretChat`). */
class EncryptionKeyHashTest {
    @Test
    fun `36 bytes make 144 pixels, a 12 by 12 image`() {
        val hash = EncryptionKeyHash(ByteArray(36))

        assertEquals(EncryptionKeyHash.IMAGE_SIZE * EncryptionKeyHash.IMAGE_SIZE, hash.pixelCount)
        assertFalse(hash.isEmpty)
    }

    @Test
    fun `each byte gives four pixels of two bits, low bits first`() {
        // 0b11_10_01_00: pixels 0, 1, 2, 3 from the lowest bits up; then 0b00_01_10_11 (0x1B): 3, 2, 1, 0.
        val hash = EncryptionKeyHash(byteArrayOf(0b11100100.toByte(), 0x1B))

        assertEquals(listOf(0, 1, 2, 3, 3, 2, 1, 0), (0 until hash.pixelCount).map(hash::pixel))
    }

    @Test
    fun `the first 32 bytes as lowercase hexadecimal pairs`() {
        val hash = EncryptionKeyHash(ByteArray(36) { (it * 8).toByte() })

        val pairs = hash.hexPairs()

        assertEquals(EncryptionKeyHash.HEX_BYTES, pairs.size)
        assertEquals(listOf("00", "08", "10", "18"), pairs.take(4))
        assertEquals("f8", pairs.last())
    }

    @Test
    fun `hashes compare by content and keep their own copy`() {
        val bytes = ByteArray(36) { it.toByte() }
        val hash = EncryptionKeyHash(bytes)

        bytes[0] = 99

        assertEquals(EncryptionKeyHash(ByteArray(36) { it.toByte() }), hash)
        assertNotEquals(EncryptionKeyHash(bytes), hash)
        assertTrue(EncryptionKeyHash.Empty.isEmpty)
    }
}
