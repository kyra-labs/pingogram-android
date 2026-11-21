package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.theme.WallpaperBarColors
import dev.kyralabs.pingogram.core.ui.theme.WallpaperBarTheme
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.wallpaperBarColors
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer
import dev.kyralabs.pingogram.core.ui.wallpaper.StatusBarIcons
import dev.kyralabs.pingogram.core.ui.wallpaper.WallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberWallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberWallpaperTopLuminance
import dev.kyralabs.pingogram.core.ui.wallpaper.wallpaperFrame

/**
 * The conversation's wallpaper, which `ChatRoute` provides from [ChatWallpaperViewModel]; the theme-color one alone
 * where nothing provides it (previews, screenshot tests).
 */
internal val LocalConversationWallpaper = compositionLocalOf { ConversationWallpaper() }

/**
 * The wallpaper behind the conversation's bars and messages (`docs/DESIGN_SYSTEM.md` §6 "Chat appearance"): the chat's
 * own, else the account's for the theme dimmed as Chat appearance says in dark theme, else the theme-color one. In
 * [frame], the conversation's window, it draws its part of the window's wallpaper; [blurred], the copy behind the top
 * bar; [placeholder], what shows until a photo is drawn where no theme-color wallpaper is chosen.
 */
@Composable
internal fun ConversationWallpaperLayer(
    modifier: Modifier = Modifier,
    frame: WallpaperFrame? = null,
    blurred: Boolean = false,
    placeholder: Color = Color.Unspecified,
) {
    val shown = LocalConversationWallpaper.current.forTheme(isDarkTheme())
    ChatWallpaperLayer(shown.wallpaper, shown.darkThemeDimming, modifier, frame, blurred, placeholder)
}

/**
 * How a conversation shows over its Telegram wallpaper (`docs/DESIGN_SYSTEM.md` §13.5, owner decision 2026-11-21): the
 * wallpaper fills the window ([frame]), the top bar shows it blurred in the scheme of its top's brightness ([bar]: the
 * theme's until a photo's is measured) and the conversation's colors ([seed]), and the composer floats over it.
 */
@Immutable
internal class ConversationBackdrop(
    val frame: WallpaperFrame,
    val bar: WallpaperBarColors,
    val seed: Int?,
)

/** The conversation's [ConversationBackdrop]; null where it has no Telegram wallpaper, which keeps today's look. */
@Composable
internal fun rememberConversationBackdrop(): ConversationBackdrop? {
    val dark = isDarkTheme()
    val shown = LocalConversationWallpaper.current.forTheme(dark)
    val frame = rememberWallpaperFrame()
    val luminance = rememberWallpaperTopLuminance(shown.wallpaper, shown.darkThemeDimming)
    val bar = wallpaperBarColors(luminance ?: if (dark) 0f else 1f)
    return remember(shown.wallpaper, frame, bar, shown.seed) {
        shown.wallpaper?.let { ConversationBackdrop(frame, bar, shown.seed) }
    }
}

/** The [ConversationBackdrop] the conversation's parts draw for; null for today's look. */
internal val LocalConversationBackdrop = staticCompositionLocalOf<ConversationBackdrop?> { null }

/**
 * [content] given the conversation's backdrop ([rememberConversationBackdrop]): where it has a Telegram wallpaper, in
 * the window's [WallpaperFrame], with the status bar's icons for its top and [LocalConversationBackdrop] set for the
 * parts below; elsewhere as today.
 */
@Composable
internal fun ConversationBackdropBox(
    modifier: Modifier,
    content: @Composable (ConversationBackdrop?) -> Unit,
) {
    val backdrop = rememberConversationBackdrop()
    StatusBarIcons(backdrop?.bar?.darkStatusBarIcons)
    Box(modifier.then(backdrop?.let { Modifier.wallpaperFrame(it.frame) } ?: Modifier)) {
        CompositionLocalProvider(LocalConversationBackdrop provides backdrop) { content(backdrop) }
    }
}

/**
 * Over a Telegram wallpaper, what shows behind the top bar: the wallpaper blurred and washed, from the window's top to
 * the end of the messages' sheet's rounded corners ([innerPadding]'s top, as `ContentSheet` places the sheet), the only
 * place it shows, so the messages read as a rounded container below the bar. Clipped in the draw phase, so a collapsing
 * bar redraws it without recomposing, and nothing of it is drawn under the sheet's opaque wallpaper. Nothing elsewhere.
 */
@Composable
private fun ConversationBarBackdrop(innerPadding: PaddingValues) {
    val backdrop = LocalConversationBackdrop.current ?: return
    val corner = MaterialTheme.shapes.extraLarge.topStart
    Box(
        Modifier.fillMaxSize().drawWithContent {
            val bottom = innerPadding.calculateTopPadding().toPx() + corner.toPx(size, this)
            clipRect(bottom = bottom) { this@drawWithContent.drawContent() }
        },
    ) {
        // Until a photo is drawn, the bar's color without a wallpaper, not the window's.
        val placeholder = MaterialTheme.colorScheme.surfaceContainer
        ConversationWallpaperLayer(
            Modifier.matchParentSize(),
            backdrop.frame,
            blurred = true,
            placeholder = placeholder,
        )
        WallpaperBarTheme(backdrop.bar, backdrop.seed) {
            Spacer(Modifier.matchParentSize().background(backdrop.bar.wash()))
        }
    }
}

/**
 * The top bar's slot: over a wallpaper, [content] in the scheme of its top's brightness and the conversation's colors,
 * clear over the washed blur ([ConversationBackdropBox]); elsewhere as today, on `surfaceContainer`.
 */
@Composable
internal fun ConversationBarSlot(content: @Composable (barColor: Color) -> Unit) {
    val backdrop = LocalConversationBackdrop.current
    if (backdrop == null) {
        content(MaterialTheme.colorScheme.surfaceContainer)
    } else {
        WallpaperBarTheme(backdrop.bar, backdrop.seed) { content(Color.Transparent) }
    }
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
