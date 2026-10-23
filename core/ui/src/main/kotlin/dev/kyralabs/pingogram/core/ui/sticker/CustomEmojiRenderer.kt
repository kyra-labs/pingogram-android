package dev.kyralabs.pingogram.core.ui.sticker

import android.animation.ValueAnimator
import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.WindowInfo
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Draws the custom emoji of texts (`docs/ARCHITECTURE.md`, Custom emoji; `docs/PERFORMANCE.md` §4): the conversation
 * hands it the custom emoji loaded so far ([emoji], snapshot state that only the drawing nodes read, in coroutines and
 * the draw phase, so a newly loaded emoji recomposes nothing), and texts draw them over their fallback emoji with
 * `Modifier.customEmoji` (reactions with [CustomEmojiImage]). [LocalCustomEmoji] provides it; without one (previews,
 * screens that load none) texts show the fallback emoji.
 *
 * - **Animated (TGS)**: through the app's [stickers], so they share the stickers' decoded animations, their frame cache
 *   (keyed by file and size) and their cap of animating items: each custom emoji that plays takes one of its slots
 *   (the same id twice in one text takes one), and plays only while on screen and the window has the focus.
 * - **Still (WebP)**: the picture itself, and for a video (WebM) one its thumbnail, from [stills] at the emoji's size;
 *   an animated one shows its thumbnail where there is no [stickers] (previews).
 */
@Stable
class CustomEmojiRenderer(
    internal val stickers: AnimatedStickers?,
    internal val stills: CustomEmojiStills,
) {
    /** The custom emoji known so far, by id; an id missing from it shows its fallback emoji. */
    var emoji: ImmutableMap<Long, CustomEmoji> by mutableStateOf(persistentMapOf())

    /**
     * The still pictures loaded so far, by file and size: the same bitmaps as Coil's memory cache holds, kept here too
     * so a text that scrolls in shows them in its first frame, without waiting for a load. One renderer per screen, so
     * they go with it, before TDLib's next session could reuse a file id.
     */
    private val loadedStills = LruCache<StillKey, ImageBitmap>(MAX_STILLS)

    /**
     * What [emoji] shows at once at [sizePx]: an animated one's first frame from the stickers' frame cache, else its
     * still picture if loaded; null where it has to load first. Never waits.
     */
    internal fun cachedFrame(
        emoji: CustomEmoji,
        sizePx: Int,
    ): ImageBitmap? {
        val sticker = emoji.sticker
        val stickers = stickers
        if (sticker.format == StickerFormat.Tgs && stickers != null) {
            stickers.cachedFirstFrame(sticker.file.id, sizePx, sizePx)?.let { return it.asImageBitmap() }
        }
        return sticker.stillPicture()?.let { loadedStills[StillKey(it, sizePx)] }
    }

    /** Whether [emoji] may play: an animated one, with animations on; it needs its player even with a cached frame. */
    internal fun mayPlay(emoji: CustomEmoji): Boolean =
        emoji.sticker.format == StickerFormat.Tgs && stickers?.animate == true && ValueAnimator.areAnimatorsEnabled()

    /** The still picture of TDLib file [fileId] at [sizePx], kept in [loadedStills]. */
    internal suspend fun still(
        fileId: Int,
        sizePx: Int,
    ): ImageBitmap? {
        val key = StillKey(fileId, sizePx)
        return loadedStills[key] ?: stills.load(fileId, sizePx)?.also { loadedStills.put(key, it) }
    }

    private data class StillKey(
        val fileId: Int,
        val sizePx: Int,
    )

    private companion object {
        /** About two screens of distinct custom emoji; the bitmaps are Coil's, a few kilobytes each. */
        const val MAX_STILLS = 128
    }
}

/** Loads the still picture of a custom emoji: TDLib file `fileId` at `sizePx` × `sizePx`, fitted; null if it fails. */
fun interface CustomEmojiStills {
    suspend fun load(
        fileId: Int,
        sizePx: Int,
    ): ImageBitmap?
}

/**
 * [CustomEmojiStills] through the app's Coil image loader and its memory cache ([TdlibFileImage], which downloads the
 * file at visible priority), as stickers' still pictures load.
 */
class CoilCustomEmojiStills(
    private val context: Context,
) : CustomEmojiStills {
    override suspend fun load(
        fileId: Int,
        sizePx: Int,
    ): ImageBitmap? {
        val request =
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(sizePx)
                .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        return result?.image?.toBitmap()?.asImageBitmap()
    }
}

/**
 * The [CustomEmojiRenderer] of a screen, following [emoji] (the screen's view model's custom emoji) while it is
 * composed, with the app's animated stickers ([LocalAnimatedStickers]) and still pictures from Coil.
 */
@Composable
fun rememberCustomEmojiRenderer(emoji: StateFlow<ImmutableMap<Long, CustomEmoji>>): CustomEmojiRenderer {
    val stickers = LocalAnimatedStickers.current
    val context = LocalContext.current
    val renderer = remember(stickers, context) { CustomEmojiRenderer(stickers, CoilCustomEmojiStills(context)) }
    FollowEffect(renderer, emoji)
    return renderer
}

/** Hands [renderer] every value of [emoji]; its own composable, so nothing around it reads them. */
@Composable
private fun FollowEffect(
    renderer: CustomEmojiRenderer,
    emoji: Flow<ImmutableMap<Long, CustomEmoji>>,
) {
    LaunchedEffect(renderer, emoji) { emoji.collect { renderer.emoji = it } }
}

/** The [CustomEmojiRenderer] of the screen; null where custom emoji show their fallback emoji. */
val LocalCustomEmoji = staticCompositionLocalOf<CustomEmojiRenderer?> { null }

/**
 * Shows [emoji] at [sizePx] × [sizePx] through [show]: the first frame of an animated one, then its frames while it is
 * on screen (the draws of [frames]' node, and [windowInfo]'s focus, as [StickerPlayer] takes them), for as long as the
 * caller runs; else its still picture. Shows nothing if neither loads: the fallback emoji stays.
 */
internal suspend fun CustomEmojiRenderer.play(
    emoji: CustomEmoji,
    sizePx: Int,
    frames: CustomEmojiFrames,
    windowInfo: WindowInfo,
    show: (ImageBitmap) -> Unit,
) {
    val sticker = emoji.sticker
    val stickers = stickers
    if (sticker.format == StickerFormat.Tgs && stickers != null) {
        val animation = stickers.animation(sticker.file.id)
        if (animation != null) {
            show(stickers.firstFrame(sticker.file.id, animation, sizePx, sizePx).asImageBitmap())
            if (stickers.plays(animation)) {
                StickerPlayer(stickers, animation, sizePx, sizePx, frames.draws, show)
                    .playOnScreen(windowInfo, frames.requestDraw)
            }
            return
        }
    }
    val still = sticker.stillPicture() ?: return
    still(still, sizePx)?.let(show)
}

/** The file of this sticker's still picture: itself if it is one, else its thumbnail if that is a picture. */
private fun Sticker.stillPicture(): Int? =
    if (format == StickerFormat.Webp) {
        file.id
    } else {
        thumbnail?.takeIf { it.format in PICTURE_FORMATS }?.file?.id
    }

private val PICTURE_FORMATS = setOf(ThumbnailFormat.Jpeg, ThumbnailFormat.Png, ThumbnailFormat.Webp)
