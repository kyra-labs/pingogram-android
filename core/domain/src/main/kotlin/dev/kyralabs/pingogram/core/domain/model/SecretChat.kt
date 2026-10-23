package dev.kyralabs.pingogram.core.domain.model

/**
 * A secret chat as TDLib knows it (`SecretChatRepository`, `REQUIREMENTS.md` FR-E1), behind a chat of
 * [ChatType.Secret]: an end-to-end encrypted chat with user [userId] that exists on this device only. TDLib does all of
 * its cryptography; this holds what the UI shows of it. Plain data, mapped from TDLib's `secretChat` by `core:data`.
 *
 * `toString()` leaves the key hash out, so a secret chat can be logged.
 */
data class SecretChat(
    val id: Int,
    val userId: Long,
    val state: SecretChatState,
    /** The current user started the chat; false when the other user did, and TDLib accepted it. */
    val isOutbound: Boolean,
    /** What both sides compare to check that nobody sits between them; empty until the key is agreed. */
    val keyHash: EncryptionKeyHash,
    /** The protocol layer the other user's app supports (td_api.tl `secretChat.layer`). */
    val layer: Int,
) {
    override fun toString() = "SecretChat(id=$id, state=$state, isOutbound=$isOutbound, layer=$layer, …)"
}

/** Where a [SecretChat] is (td_api.tl `SecretChatState`). */
enum class SecretChatState {
    /** Waiting for the other user to come online and agree on the key; nothing can be sent yet. */
    Pending,

    /** The key is agreed: messages can be sent. */
    Ready,

    /** Ended by either side, or refused; nothing can be sent any more. */
    Closed,
}

/**
 * The hash of a secret chat's key (td_api.tl `secretChat.key_hash`), which both people compare on their screens: 36
 * bytes, drawn as a 12 × 12 image of four colors, two bits per pixel, low bits first ([pixel]); the first 32 bytes
 * can also be written as 32 hexadecimal pairs ([hexPairs]). TDLib documents only the 36-byte form; a hash of another
 * size draws as many pixels as it has bits for, row by row.
 *
 * Compared by content; the bytes are copied in and never handed out.
 */
class EncryptionKeyHash(
    bytes: ByteArray,
) {
    private val bytes = bytes.copyOf()

    /** The hash is known: TDLib sends an empty one until the key is agreed. */
    val isEmpty: Boolean get() = bytes.isEmpty()

    /** The pixels of the image: four per byte. */
    val pixelCount: Int get() = bytes.size * PIXELS_PER_BYTE

    /**
     * The color of pixel [index] (0 to [pixelCount] − 1, left to right, top to bottom), from 0 to 3: the two bits of
     * byte `index / 4` at bit `2 × (index % 4)`, as td_api.tl splits the little-endian bytes into groups of 2 bits.
     */
    fun pixel(index: Int): Int {
        val byte = bytes[index / PIXELS_PER_BYTE].toInt()
        return (byte shr (BITS_PER_PIXEL * (index % PIXELS_PER_BYTE))) and PIXEL_MASK
    }

    /** The first [HEX_BYTES] bytes as lowercase hexadecimal pairs ("1f", "a0", …), fewer for a shorter hash. */
    fun hexPairs(): List<String> =
        bytes.take(HEX_BYTES).map {
            (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }

    override fun equals(other: Any?) = other is EncryptionKeyHash && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()

    override fun toString() = "EncryptionKeyHash(${bytes.size} bytes)"

    companion object {
        /** No hash yet. */
        val Empty = EncryptionKeyHash(ByteArray(0))

        /** Pixels in a row of the image. */
        const val IMAGE_SIZE = 12

        /** Bytes td_api.tl writes as hexadecimal. */
        const val HEX_BYTES = 32

        private const val PIXELS_PER_BYTE = 4
        private const val BITS_PER_PIXEL = 2
        private const val PIXEL_MASK = 0b11
        private const val BYTE_MASK = 0xFF
        private const val HEX_RADIX = 16
    }
}
