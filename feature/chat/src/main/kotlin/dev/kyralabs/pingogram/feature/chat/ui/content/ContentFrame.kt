package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.sizes

// How the bubble frames each content component of this package. The components draw the content only; the bubble
// adds its background, padding, caption, time and reactions. Three frames:
//
// - Bare (isBare): no bubble background at all, the content stands on the chat's background with the time
//   below or over it: stickers, animated emoji, dice and video messages.
// - Edge to edge (isEdgeToEdge): photos, videos and GIFs (and albums of them) fill the bubble to its edges. They
//   clip themselves to the `shape` the bubble passes them, so the bubble decides the corners (for instance square
//   bottom corners above a caption); the caption goes below them with the bubble's padding.
// - Padded (neither): voice messages, music, files, locations, contacts, polls and the rest sit inside the bubble's
//   padding like text. Their accents come from the bubble's `BubbleColors`, which the bubble passes as `colors`.

/** The content is shown without a bubble: stickers, animated emoji, dice and video messages. */
internal val MessageContent.isBare: Boolean
    get() =
        when (this) {
            is MessageContent.Sticker,
            is MessageContent.AnimatedEmoji,
            is MessageContent.Dice,
            is MessageContent.VideoNote,
            -> true

            else -> false
        }

/**
 * The content fills its bubble to the edges, clipped to the shape the bubble passes: photos, videos and GIFs, alone or
 * in an album.
 */
internal val MessageContent.isEdgeToEdge: Boolean
    get() =
        when (this) {
            is MessageContent.Photo,
            is MessageContent.Video,
            is MessageContent.Animation,
            -> true

            else -> false
        }

/**
 * How wide edge-to-edge content is shown, at most [maxWidth]: a photo's, video's or GIF's width ([mediaDisplaySize]),
 * or [maxWidth] for an album ([isAlbum]). The bubble takes this width, so a caption below wraps within the media.
 */
@Composable
internal fun edgeToEdgeWidth(
    content: MessageContent,
    isAlbum: Boolean,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
): Dp {
    val (width, height) = content.mediaDimensions()?.takeUnless { isAlbum } ?: return maxWidth
    val sizes = MaterialTheme.sizes
    return mediaDisplaySize(width, height, maxWidth, sizes.mediaMaxHeight, sizes.mediaMinSize).width
}

/** A photo's (its largest size), video's or GIF's width and height in pixels; null for other content. */
private fun MessageContent.mediaDimensions(): Pair<Int, Int>? =
    when (this) {
        is MessageContent.Photo -> largestPhotoSize(photo.sizes).let { (it?.width ?: 0) to (it?.height ?: 0) }
        is MessageContent.Video -> video.width to video.height
        is MessageContent.Animation -> animation.width to animation.height
        else -> null
    }

/**
 * The file of a voice message, a song or a document, whose download the conversation follows and shows; null for
 * other content, whose images Coil loads by itself.
 */
internal val MessageContent.downloadableFileId: Int?
    get() =
        when (this) {
            is MessageContent.VoiceNote -> voiceNote.file.id
            is MessageContent.Audio -> audio.file.id
            is MessageContent.Document -> document.file.id
            else -> null
        }
