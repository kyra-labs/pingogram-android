package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.feature.chat.ui.content.TdlibImage
import dev.kyralabs.pingogram.feature.chat.ui.content.fadingOutAs
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberImageAlpha

/**
 * A reaction's emoji in [style]; the star of the paid reaction; for a custom emoji its picture, TDLib file
 * [customEmojiPicture], as large as an emoji of [style], or, without one or until it fades in, a dot of
 * [customEmojiSize] in [placeholderColor]. Custom emoji are not animated: that comes with the sticker renderer.
 */
@Composable
internal fun ReactionGlyph(
    label: ReactionLabel,
    style: TextStyle,
    customEmojiSize: Dp,
    placeholderColor: ColorProducer,
    customEmojiPicture: Int? = null,
) {
    when (label) {
        is ReactionLabel.Emoji -> {
            Text(label.emoji, style = style)
        }

        ReactionLabel.Paid -> {
            Text(PAID_REACTION_EMOJI, style = style)
        }

        is ReactionLabel.CustomEmoji -> {
            if (customEmojiPicture == null) {
                CustomEmojiPlaceholder(customEmojiSize, placeholderColor)
            } else {
                CustomEmojiPicture(customEmojiPicture, style, customEmojiSize, placeholderColor)
            }
        }
    }
}

/**
 * A custom emoji's picture, TDLib file [fileId], in a square as wide as [style]'s font size, fitted; over its
 * placeholder, which fades out as the picture fades in.
 */
@Composable
private fun CustomEmojiPicture(
    fileId: Int,
    style: TextStyle,
    placeholderSize: Dp,
    placeholderColor: ColorProducer,
) {
    val density = LocalDensity.current
    val side = with(density) { style.fontSize.toDp() }
    val sidePx = with(density) { side.roundToPx() }
    val alpha = rememberImageAlpha(fileId)
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        CustomEmojiPlaceholder(placeholderSize, placeholderColor, Modifier.fadingOutAs(alpha))
        TdlibImage(
            fileId = fileId,
            widthPx = sidePx,
            heightPx = sidePx,
            alpha = alpha,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Where a custom emoji goes without its picture: a dot of the emoji's size, its color read when drawn. */
@Composable
private fun CustomEmojiPlaceholder(
    size: Dp,
    color: ColorProducer,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .size(size)
            .drawBehind { drawCircle(color()) },
    )
}

/** The paid star reaction of channels. */
private const val PAID_REACTION_EMOJI = "⭐"
