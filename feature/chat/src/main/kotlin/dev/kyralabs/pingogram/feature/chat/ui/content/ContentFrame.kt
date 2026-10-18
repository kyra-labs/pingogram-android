package dev.kyralabs.pingogram.feature.chat.ui.content

import dev.kyralabs.pingogram.core.domain.model.MessageContent

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
