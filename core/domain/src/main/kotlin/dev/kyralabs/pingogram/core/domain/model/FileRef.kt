package dev.kyralabs.pingogram.core.domain.model

/**
 * A file TDLib knows, by its id (`docs/ARCHITECTURE.md`, Files and media): a photo size, a video, a voice note, an
 * avatar. `FileRepository` downloads it and reports its state; models only carry the reference.
 *
 * [size] is in bytes, 0 when TDLib does not know it yet.
 */
data class FileRef(
    val id: Int,
    val size: Long,
)

/**
 * A tiny JPEG (about 40 px wide) that TDLib sends with photos, videos and avatars, shown blurred until the real image
 * is downloaded (`docs/ARCHITECTURE.md`, Files and media).
 */
class Minithumbnail(
    val width: Int,
    val height: Int,
    val jpeg: ByteArray,
) {
    // Compares the bytes, not the array instance, so models holding one stay equal across mapping.
    override fun equals(other: Any?): Boolean =
        other is Minithumbnail && width == other.width && height == other.height && jpeg.contentEquals(other.jpeg)

    override fun hashCode(): Int = (width * HASH_MULTIPLIER + height) * HASH_MULTIPLIER + jpeg.contentHashCode()

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}
