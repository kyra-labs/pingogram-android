package dev.kyralabs.pingogram.core.data.attachment

import kotlin.math.max
import kotlin.math.roundToInt

// The size math of preparing photos and thumbnails: pure functions, unit-tested without Android.

/**
 * How an image is stored relative to how it is seen: turned clockwise by [rotation] degrees (0, 90, 180, 270) after
 * being mirrored horizontally if [mirrored], from EXIF's orientation tag.
 */
internal data class Orientation(
    val rotation: Int,
    val mirrored: Boolean,
) {
    /** Width and height swap between the stored image and the upright one. */
    val swapsSides: Boolean get() = rotation == QUARTER_TURN || rotation == THREE_QUARTER_TURN

    companion object {
        val Normal = Orientation(0, mirrored = false)

        /**
         * The orientation for EXIF's `Orientation` tag (1–8, TIFF 6.0): 1 upright, 3 upside down, 6 and 8 turned a
         * quarter, 2, 4, 5 and 7 their mirror images. Any other value counts as upright.
         */
        fun fromExif(value: Int): Orientation =
            when (value) {
                EXIF_FLIP_HORIZONTAL -> Orientation(0, mirrored = true)

                EXIF_ROTATE_180 -> Orientation(HALF_TURN, mirrored = false)

                EXIF_FLIP_VERTICAL -> Orientation(HALF_TURN, mirrored = true)

                // Mirrored, then turned: the transpose is the mirror image turned three quarters, the transverse one
                // turned a quarter (androidx ExifInterface's getRotationDegrees and isFlipped say the same).
                EXIF_TRANSPOSE -> Orientation(THREE_QUARTER_TURN, mirrored = true)

                EXIF_ROTATE_90 -> Orientation(QUARTER_TURN, mirrored = false)

                EXIF_TRANSVERSE -> Orientation(QUARTER_TURN, mirrored = true)

                EXIF_ROTATE_270 -> Orientation(THREE_QUARTER_TURN, mirrored = false)

                else -> Normal
            }
    }
}

/**
 * How a stored image of [storedWidth] × [storedHeight] becomes the photo to send: decoded at 1/[sampleSize] of its
 * size (a power of two that keeps it at least as large as needed), turned and mirrored as [orientation] says, and
 * scaled to [width] × [height], the upright size.
 */
internal data class PhotoPlan(
    val storedWidth: Int,
    val storedHeight: Int,
    val orientation: Orientation,
    val sampleSize: Int,
    val width: Int,
    val height: Int,
)

/**
 * The plan for a photo stored at [storedWidth] × [storedHeight] in [orientation]: upright, its longer side at most
 * [maxSide] pixels, never enlarged; aspect ratio kept, no side below 1 pixel.
 */
internal fun photoPlan(
    storedWidth: Int,
    storedHeight: Int,
    orientation: Orientation,
    maxSide: Int = MAX_PHOTO_SIDE,
): PhotoPlan {
    val (uprightWidth, uprightHeight) =
        if (orientation.swapsSides) storedHeight to storedWidth else storedWidth to storedHeight
    val (width, height) = fitWithin(uprightWidth, uprightHeight, maxSide)
    // The stored sides that become width and height.
    val (neededWidth, neededHeight) = if (orientation.swapsSides) height to width else width to height
    var sampleSize = 1
    while (storedWidth / (sampleSize * 2) >= neededWidth && storedHeight / (sampleSize * 2) >= neededHeight) {
        sampleSize *= 2
    }
    return PhotoPlan(storedWidth, storedHeight, orientation, sampleSize, width, height)
}

/**
 * [width] × [height] scaled down so the longer side is at most [maxSide], keeping the aspect ratio, never enlarged;
 * no side below 1. For photos ([MAX_PHOTO_SIDE]) and video thumbnails ([MAX_THUMBNAIL_SIDE]).
 */
internal fun fitWithin(
    width: Int,
    height: Int,
    maxSide: Int,
): Pair<Int, Int> {
    val longer = max(width, height)
    if (longer <= maxSide) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
    val scale = maxSide.toDouble() / longer
    return (width * scale).roundToInt().coerceIn(1, maxSide) to (height * scale).roundToInt().coerceIn(1, maxSide)
}

/** The longest side of a photo sent from Pingogram, in pixels: about what Telegram keeps of a photo. */
internal const val MAX_PHOTO_SIDE = 2560

/** The longest side of a video's thumbnail (td_api.tl `inputThumbnail`: "usually shouldn't exceed 320"). */
internal const val MAX_THUMBNAIL_SIDE = 320

/** The JPEG quality photos and thumbnails are written in: small files without visible artifacts. */
internal const val JPEG_QUALITY = 87

/**
 * The longest side of a photo sent without compression that is not a JPEG (`AttachmentKind.Original`), written anew:
 * its own size up to this, which keeps the decoded picture within memory (8192 × 6144 pixels take 192 MB).
 */
internal const val ORIGINAL_MAX_SIDE = 8192

/** The JPEG quality of a photo sent without compression that is written anew. */
internal const val ORIGINAL_JPEG_QUALITY = 100

/**
 * How much longer than wide a photo may be (td_api.tl `inputPhoto`: "width and height ratio must be at most 20");
 * a longer one goes as a file.
 */
internal const val MAX_PHOTO_ASPECT = 20

private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270

// EXIF orientation values (TIFF 6.0, tag 0x0112).
private const val EXIF_FLIP_HORIZONTAL = 2
private const val EXIF_ROTATE_180 = 3
private const val EXIF_FLIP_VERTICAL = 4
private const val EXIF_TRANSPOSE = 5
private const val EXIF_ROTATE_90 = 6
private const val EXIF_TRANSVERSE = 7
private const val EXIF_ROTATE_270 = 8
