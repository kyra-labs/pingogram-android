package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

// Media of messages, link previews and chat photos (REQUIREMENTS.md FR-C4). Files are FileRefs: FileRepository
// downloads them. Sizes are in pixels and durations in seconds, as the sender gave them.

/** A photo in several sizes, and the [minithumbnail] to show blurred until one is downloaded. */
data class Photo(
    /** In TDLib's order, usually smallest first. */
    val sizes: ImmutableList<PhotoSize>,
    val minithumbnail: Minithumbnail?,
)

/** One JPEG size of a [Photo]; [type] is Telegram's size letter (`s`, `m`, `x`, `y`, `w`, …). */
data class PhotoSize(
    val type: String,
    val width: Int,
    val height: Int,
    val file: FileRef,
)

/** A preview image of a video, a document or a sticker. */
data class Thumbnail(
    val format: ThumbnailFormat,
    val width: Int,
    val height: Int,
    val file: FileRef,
)

/** Images (JPEG, PNG, WebP, GIF) decode as pictures; MPEG4 and WebM are videos, TGS is an animated sticker. */
enum class ThumbnailFormat {
    Jpeg,
    Png,
    Webp,
    Gif,
    Mpeg4,
    Webm,
    Tgs,
}

data class Video(
    val duration: Int,
    val width: Int,
    val height: Int,
    val minithumbnail: Minithumbnail?,
    val thumbnail: Thumbnail?,
    /** It can be played while it downloads. */
    val supportsStreaming: Boolean,
    val file: FileRef,
)

/** A GIF-style animation: silent, looping, played inline. */
data class Animation(
    val duration: Int,
    val width: Int,
    val height: Int,
    /** Usually `video/mp4`; `image/gif` for the rare real GIF. */
    val mimeType: String,
    val minithumbnail: Minithumbnail?,
    val thumbnail: Thumbnail?,
    val file: FileRef,
)

data class Sticker(
    /** The sticker set it belongs to; 0 if none. */
    val setId: Long,
    val width: Int,
    val height: Int,
    /** The emoji it stands for, to show while it loads; may be empty. */
    val emoji: String,
    val format: StickerFormat,
    val thumbnail: Thumbnail?,
    val file: FileRef,
)

enum class StickerFormat {
    /** A still image. */
    Webp,

    /** A Lottie animation (gzipped JSON). */
    Tgs,

    /** A video. */
    Webm,
}

data class VoiceNote(
    val duration: Int,
    val waveform: Waveform,
    val file: FileRef,
)

/**
 * The loudness of a voice note over its length, as TDLib sends it: 5-bit levels (0–31), packed into bytes; empty if
 * unknown.
 */
class Waveform(
    val bytes: ByteArray,
) {
    // Compares the bytes, not the array instance, so models holding one stay equal across mapping.
    override fun equals(other: Any?): Boolean = other is Waveform && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString() = "Waveform(${bytes.size} bytes)"
}

/** A round video message. */
data class VideoNote(
    val duration: Int,
    /** Width and height: video notes are square, shown as a circle. */
    val length: Int,
    val minithumbnail: Minithumbnail?,
    val thumbnail: Thumbnail?,
    val file: FileRef,
)

/** A music file. [title] and [performer] may be empty; [fileName] is then the fallback. */
data class Audio(
    val duration: Int,
    val title: String,
    val performer: String,
    val fileName: String,
    val albumCoverMinithumbnail: Minithumbnail?,
    val albumCoverThumbnail: Thumbnail?,
    val file: FileRef,
)

/** Any other file. */
data class Document(
    val fileName: String,
    val mimeType: String,
    val minithumbnail: Minithumbnail?,
    val thumbnail: Thumbnail?,
    val file: FileRef,
) {
    // File names can be personal.
    override fun toString() = "Document(mimeType=$mimeType, file=$file, …)"
}

/** A point on Earth, in degrees. `toString()` leaves it out. */
data class Location(
    val latitude: Double,
    val longitude: Double,
) {
    override fun toString() = "Location(…)"
}

/** A place: a [location] with a name and an address. */
data class Venue(
    val location: Location,
    val title: String,
    val address: String,
) {
    override fun toString() = "Venue(…)"
}

/** A shared contact; [userId] is its Telegram account, 0 if unknown. `toString()` leaves out names and number. */
data class Contact(
    val phoneNumber: String,
    val firstName: String,
    val lastName: String,
    val userId: Long,
) {
    override fun toString() = "Contact(userId=$userId, …)"
}
