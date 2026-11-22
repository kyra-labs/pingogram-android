package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.theme.WallpaperBarColors
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.wallpaperBarColors
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.WallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberWallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.wallpaperFrame
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The conversation's wallpaper, which `ChatRoute` provides from [ChatWallpaperViewModel]; the theme-color one alone
 * where nothing provides it (previews, screenshot tests).
 */
internal val LocalConversationWallpaper = compositionLocalOf { ConversationWallpaper() }

/**
 * The wallpaper behind the conversation's bars and messages (`docs/DESIGN_SYSTEM.md` §6 "Chat appearance"): the chat's
 * own, else the account's for the theme dimmed as Chat appearance says in dark theme, else the theme-color one. In
 * [frame], the conversation's window, it draws its part of the window's wallpaper; [blurred], the copy behind the top
 * bar, with its [wash]; [placeholder], what shows until a photo is drawn where no theme-color wallpaper is chosen.
 */
@Composable
internal fun ConversationWallpaperLayer(
    modifier: Modifier = Modifier,
    frame: WallpaperFrame? = null,
    blurred: Boolean = false,
    placeholder: Color = Color.Unspecified,
    wash: Color = Color.Unspecified,
) {
    val shown = LocalConversationWallpaper.current.forTheme(isDarkTheme())
    ChatWallpaperLayer(shown.wallpaper, shown.darkThemeDimming, modifier, frame, blurred, placeholder, wash)
}

/**
 * How a conversation shows over its Telegram wallpaper (`docs/DESIGN_SYSTEM.md` §13.5, owner decisions 2026-11-21 and
 * 2026-11-22): the wallpaper fills the window ([frame]), the top bar shows it blurred under the theme's washed surface
 * ([bar]), in the conversation's colors, and the composer floats over it.
 */
@Immutable
internal class ConversationBackdrop(
    val frame: WallpaperFrame,
    val bar: WallpaperBarColors,
)

/** The conversation's [ConversationBackdrop]; null where it has no Telegram wallpaper, which keeps today's look. */
@Composable
internal fun rememberConversationBackdrop(): ConversationBackdrop? {
    val dark = isDarkTheme()
    val shown = LocalConversationWallpaper.current.forTheme(dark)
    val frame = rememberWallpaperFrame()
    val bar = wallpaperBarColors(dark)
    return remember(shown.wallpaper, frame, bar) { shown.wallpaper?.let { ConversationBackdrop(frame, bar) } }
}

/** The [ConversationBackdrop] the conversation's parts draw for; null for today's look. */
internal val LocalConversationBackdrop = staticCompositionLocalOf<ConversationBackdrop?> { null }

/**
 * [content] given the conversation's backdrop ([rememberConversationBackdrop]): where it has a Telegram wallpaper, in
 * the window's [WallpaperFrame], with [LocalConversationBackdrop] set for the parts below; elsewhere as today. The
 * status bar's icons stay the theme's.
 */
@Composable
internal fun ConversationBackdropBox(
    modifier: Modifier,
    content: @Composable (ConversationBackdrop?) -> Unit,
) {
    val backdrop = rememberConversationBackdrop()
    Box(modifier.then(backdrop?.let { Modifier.wallpaperFrame(it.frame) } ?: Modifier)) {
        CompositionLocalProvider(LocalConversationBackdrop provides backdrop) { content(backdrop) }
    }
}

/**
 * Over a Telegram wallpaper, what shows behind the top bar: the wallpaper blurred and washed (the wash in the same
 * layer, in a photo's one draw), from the window's top to the end of the messages' sheet's rounded corners
 * ([innerPadding]'s top, as `ContentSheet` places the sheet), the only place it shows, so the messages read as a
 * rounded container below the bar. Clipped in the draw phase, so a collapsing bar redraws it without recomposing. Below
 * the bar's bottom only the two corners keep it: between them the sheet's opaque wallpaper covers every pixel, so the
 * copy is clipped out there (whole pixels inside the sheet, one clear of its top edge and of its corners, so nothing it
 * showed changes), with rect clips, which the GPU applies without a mask (`docs/PERFORMANCE.md`, "Overdraw").
 */
@Composable
private fun ConversationBarBackdrop(innerPadding: PaddingValues) {
    val backdrop = LocalConversationBackdrop.current ?: return
    val corner = MaterialTheme.shapes.extraLarge.topStart
    // Until a photo is drawn, the bar's color without a wallpaper, not the window's.
    val placeholder = MaterialTheme.colorScheme.surfaceContainer
    ConversationWallpaperLayer(
        Modifier.fillMaxSize().drawWithContent {
            val barBottom = innerPadding.calculateTopPadding().toPx()
            val radius = corner.toPx(size, this)
            clipRect(bottom = barBottom + radius) {
                clipRect(
                    left = ceil(radius) + 1,
                    top = ceil(barBottom) + 1,
                    right = floor(size.width - radius) - 1,
                    bottom = size.height,
                    clipOp = ClipOp.Difference,
                ) { this@drawWithContent.drawContent() }
            }
        },
        backdrop.frame,
        blurred = true,
        placeholder = placeholder,
        wash = backdrop.bar.wash(),
    )
}

/**
 * The top bar's slot: over a wallpaper, [content] clear over the washed blur ([ConversationBarBackdrop]), in the
 * theme's `onSurface`; elsewhere as today, on `surfaceContainer`.
 */
@Composable
internal fun ConversationBarSlot(content: @Composable (barColor: Color) -> Unit) {
    val backdrop = LocalConversationBackdrop.current
    content(if (backdrop == null) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
}

/**
 * The messages' `ContentSheet` on the conversation's wallpaper (`docs/DESIGN_SYSTEM.md` §6, §13.5): the chat's own, the
 * account's or the theme-color one, in a layer of its own so scrolling neither composes nor redraws it. Over a Telegram
 * wallpaper the sheet and its surround are clear: it shows its part of the window's wallpaper sharp, and the wallpaper
 * blurred behind the top bar and its rounded corners ([ConversationBarBackdrop]). The wallpaper is opaque, so nothing
 * is drawn under it (`docs/PERFORMANCE.md`, "Overdraw"); a photo shows the sheet's `surface` in its place until it is
 * drawn, and if it cannot be, never the window's color.
 */
@Composable
internal fun ConversationSheet(
    innerPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val backdrop = LocalConversationBackdrop.current
    val colors = MaterialTheme.colorScheme
    ConversationBarBackdrop(innerPadding)
    ContentSheet(
        innerPadding,
        modifier,
        color = if (backdrop != null) Color.Transparent else colors.surface,
        surround = if (backdrop != null) Color.Transparent else colors.surfaceContainer,
    ) { contentPadding ->
        ConversationWallpaperLayer(Modifier.matchParentSize(), backdrop?.frame, placeholder = colors.surface)
        content(contentPadding)
    }
}
