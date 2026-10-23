package dev.kyralabs.pingogram.core.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage

/**
 * A sticker set's picture in a list (Settings → Stickers): [sticker], still, fitted into a square of [size], its
 * picture (the sticker itself if it is a still, else its thumbnail if that is a picture) loaded by Coil at that size
 * over its emoji, which stands in until it shows and where there is none. Without a sticker, a tonal `small`-rounded
 * square in `surfaceContainerHigh`. Decorative: the row names the set.
 */
@Composable
fun StickerCover(
    sticker: Sticker?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val picture = sticker?.stillPicture()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            sticker == null -> {
                Box(
                    Modifier
                        .size(size)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small),
                )
            }

            else -> {
                // The emoji until the picture shows: stickers are transparent, so it would show through.
                var pictureShown by remember(picture) { mutableStateOf(false) }
                if (!pictureShown && sticker.emoji.isNotBlank()) CoverEmoji(sticker.emoji, size)
                if (picture != null) CoverPicture(picture, size) { pictureShown = true }
            }
        }
    }
}

@Composable
private fun CoverEmoji(
    emoji: String,
    size: Dp,
) {
    val style = MaterialTheme.typography.titleLarge
    val density = LocalDensity.current
    val emojiStyle =
        remember(style, density, size) {
            val fontSize = with(density) { (size * EMOJI_SIZE_RATIO).toSp() }
            style.copy(fontSize = fontSize, lineHeight = fontSize, textAlign = TextAlign.Center)
        }
    Text(text = emoji, maxLines = 1, softWrap = false, style = emojiStyle)
}

@Composable
private fun CoverPicture(
    fileId: Int,
    size: Dp,
    onShown: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val request =
        remember(context, fileId, sizePx) {
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(sizePx)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
        onSuccess = { onShown() },
    )
}

// An emoji glyph is about 1.2 times as tall as its font size: at this ratio it fills the square with a margin.
private const val EMOJI_SIZE_RATIO = 0.7f
