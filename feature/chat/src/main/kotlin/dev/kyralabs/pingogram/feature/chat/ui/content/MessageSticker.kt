package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.ui.sticker.AnimatedSticker
import dev.kyralabs.pingogram.core.ui.sticker.AnimatedStickers
import dev.kyralabs.pingogram.core.ui.sticker.LocalAnimatedStickers
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A sticker message (`REQUIREMENTS.md` FR-C4), bare ([isBare]): the sticker fitted into a `sticker`-sized square,
 * keeping its aspect ratio, on the chat's background.
 *
 * - **Still (WebP)** stickers are loaded by Coil at that size.
 * - **Animated (TGS)** stickers play once their file is downloaded ([StickerImage]), their thumbnail before, when it
 *   is a picture; **video (WebM)** stickers show their thumbnail.
 * - Until the picture shows, and when there is none, the sticker's emoji stands in for it, or, for a sticker without
 *   one, a rounded tonal shape ([StickerStandIn]); it fades out as the picture fades in. Stickers have transparent
 *   backgrounds, so nothing else is drawn behind them, and they are never dimmed in dark theme: they are artwork.
 *
 * Screen readers hear "😀 Sticker".
 */
@Composable
internal fun MessageSticker(
    content: MessageContent.Sticker,
    modifier: Modifier = Modifier,
) {
    val sticker = content.sticker
    val description =
        if (sticker.emoji.isBlank()) {
            stringResource(UiR.string.preview_sticker)
        } else {
            stringResource(UiR.string.preview_sticker_emoji, sticker.emoji)
        }
    StickerImage(
        sticker = sticker,
        displaySize = fitInSquare(sticker.width, sticker.height, MaterialTheme.sizes.sticker),
        modifier = modifier.imageSemantics(description),
    )
}

/**
 * A message of just one emoji (`REQUIREMENTS.md` FR-C4), bare ([isBare]): its sticker in a `largeEmoji`-sized
 * square, as [MessageSticker] shows stickers, or the emoji itself that large when there is no sticker.
 */
@Composable
internal fun MessageAnimatedEmoji(
    content: MessageContent.AnimatedEmoji,
    modifier: Modifier = Modifier,
) {
    val side = MaterialTheme.sizes.largeEmoji
    val sticker = content.sticker
    if (sticker != null) {
        StickerImage(
            sticker = sticker,
            displaySize = fitInSquare(sticker.width, sticker.height, side),
            modifier = modifier.imageSemantics(content.emoji),
        )
    } else {
        LargeEmoji(content.emoji, side, modifier.size(side).imageSemantics(content.emoji))
    }
}

/**
 * A die thrown in the chat (`REQUIREMENTS.md` FR-C4), bare ([isBare]): its emoji (🎲, 🎯, 🏀, …) in a
 * `largeEmoji`-sized square. Telegram animates the throw to its value; that comes with the sticker renderer.
 */
@Composable
internal fun MessageDice(
    content: MessageContent.Dice,
    modifier: Modifier = Modifier,
) {
    val side = MaterialTheme.sizes.largeEmoji
    LargeEmoji(content.emoji, side, modifier.size(side).imageSemantics(content.emoji))
}

/**
 * [sticker] fitted into [displaySize]: its picture (the sticker itself if it is a still, else its thumbnail if that
 * is a picture) over its emoji, which fades out as the picture fades in. An animated (TGS) sticker plays through the
 * app's [AnimatedStickers] ([LocalAnimatedStickers]; none in previews) once its file is downloaded: its first frame
 * replaces the picture and the emoji at once, as they show the same artwork (unless [animate] is false, as for a set's
 * picture in the sticker panel). Video (WebM) stickers keep their thumbnail.
 */
@Composable
internal fun StickerImage(
    sticker: Sticker,
    displaySize: DpSize,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val pictureId =
        when (sticker.format) {
            StickerFormat.Webp -> {
                sticker.file.id
            }

            StickerFormat.Tgs, StickerFormat.Webm -> {
                sticker.thumbnail
                    ?.asPicture()
                    ?.file
                    ?.id
            }
        }
    val animated = LocalAnimatedStickers.current?.takeIf { animate && sticker.format == StickerFormat.Tgs }
    // 1 once the animation shows its first frame; read in the draw phase.
    val animationShown = remember(sticker.file.id) { mutableFloatStateOf(0f) }
    val hiddenByAnimation = Modifier.graphicsLayer { alpha = 1f - animationShown.floatValue }
    Box(modifier.size(displaySize), contentAlignment = Alignment.Center) {
        if (pictureId == null) {
            StickerStandIn(sticker.emoji, displaySize, hiddenByAnimation)
        } else {
            val alpha = rememberImageAlpha(pictureId)
            StickerStandIn(sticker.emoji, displaySize, hiddenByAnimation.fadingOutAs(alpha))
            val density = LocalDensity.current
            TdlibImage(
                fileId = pictureId,
                widthPx = with(density) { displaySize.width.roundToPx() },
                heightPx = with(density) { displaySize.height.roundToPx() },
                alpha = alpha,
                modifier = hiddenByAnimation.matchParentSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (animated != null) {
            AnimatedSticker(
                stickers = animated,
                fileId = sticker.file.id,
                modifier = Modifier.matchParentSize(),
                onFirstFrame = { animationShown.floatValue = 1f },
            )
        }
    }
}

/**
 * What a sticker of [displaySize] shows until its picture does, and without one: its [emoji], or, when it has none, a
 * rounded tonal shape (`surfaceContainerHigh`, `extraLarge` corners), never a hard-edged block.
 */
@Composable
private fun StickerStandIn(
    emoji: String,
    displaySize: DpSize,
    modifier: Modifier = Modifier,
) {
    if (emoji.isBlank()) {
        Box(
            modifier
                .size(displaySize)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge),
        )
    } else {
        LargeEmoji(emoji, minOf(displaySize.width, displaySize.height), modifier)
    }
}

/**
 * [emoji] filling about a square of [side]: a picture rather than text, so its size follows [side] and not the font
 * scale, like an avatar's initials.
 */
@Composable
private fun LargeEmoji(
    emoji: String,
    side: Dp,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.displayLarge
    val density = LocalDensity.current
    val emojiStyle =
        remember(style, density, side) {
            val fontSize = with(density) { (side * EMOJI_SIZE_RATIO).toSp() }
            style.copy(fontSize = fontSize, lineHeight = fontSize, textAlign = TextAlign.Center)
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text = emoji, maxLines = 1, softWrap = false, style = emojiStyle)
    }
}

/** An image for screen readers, described by [description]; what is inside is not read out. */
private fun Modifier.imageSemantics(description: String): Modifier =
    clearAndSetSemantics {
        contentDescription = description
        role = Role.Image
    }

// An emoji glyph is about 1.2 times as tall as its font size: at this ratio it fills the square with a margin.
private const val EMOJI_SIZE_RATIO = 0.7f
