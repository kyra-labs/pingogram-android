package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.WallpaperBarColors
import dev.kyralabs.pingogram.core.ui.theme.WallpaperBarTheme
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.wallpaperBarColors
import dev.kyralabs.pingogram.core.ui.wallpaper.WallpaperFrame
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberPhotoTopLuminance
import dev.kyralabs.pingogram.core.ui.wallpaper.rememberWallpaperTopLuminance
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * How the preview's top bar shows over a Telegram wallpaper or a picked photo (`docs/DESIGN_SYSTEM.md` §13.5), as the
 * conversation's does: the wallpaper of [frame], the preview, blurred, in the scheme of its top's brightness ([bar]).
 */
@Immutable
internal class PreviewBackdrop(
    val frame: WallpaperFrame,
    val bar: WallpaperBarColors,
)

/**
 * The preview's [PreviewBackdrop] in [frame]; null where it shows no Telegram wallpaper nor a picked photo (a
 * theme-color one), which keeps the bar on `surfaceContainer`. Until a photo's top is measured, the theme's own.
 */
@Composable
internal fun rememberPreviewBackdrop(
    state: WallpaperPageUiState,
    frame: WallpaperFrame,
): PreviewBackdrop? {
    val picked = state.picker.pending as? WallpaperChoice.NewPhoto
    val luminance =
        if (picked != null) {
            rememberPhotoTopLuminance(picked.path, state.dimming)
        } else {
            rememberWallpaperTopLuminance(state.previewedWallpaper, state.dimming)
        }
    val bar = wallpaperBarColors(luminance ?: if (isDarkTheme()) 0f else 1f)
    val shown = picked != null || state.previewedWallpaper != null
    return remember(shown, frame, bar) { if (shown) PreviewBackdrop(frame, bar) else null }
}

/**
 * The preview's top bar: back, the chat's photo and name, or a sample's for the account's wallpaper. Over a
 * [backdrop], the wallpaper blurred and washed behind it, drawn in its bounds only, and its colors in the scheme of
 * the wallpaper's top and the wallpaper's own ([WallpaperPageUiState.seed]); else on `surfaceContainer`.
 */
@Composable
internal fun PreviewTopBar(
    state: WallpaperPageUiState,
    backdrop: PreviewBackdrop?,
    modifier: Modifier = Modifier,
) {
    if (backdrop == null) {
        PreviewTopBarRow(state.chat, MaterialTheme.colorScheme.surfaceContainer, modifier)
        return
    }
    Box(modifier) {
        PreviewWallpaper(
            pending = state.picker.pending,
            set = state.shownWallpaper,
            dimming = state.dimming,
            modifier = Modifier.matchParentSize(),
            frame = backdrop.frame,
            blurred = true,
        )
        WallpaperBarTheme(backdrop.bar, state.seed) {
            Spacer(Modifier.matchParentSize().background(backdrop.bar.wash()))
            PreviewTopBarRow(state.chat, Color.Transparent)
        }
    }
}

@Composable
private fun PreviewTopBarRow(
    chat: WallpaperPageChat?,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val name = chat?.title ?: stringResource(R.string.settings_wallpaper_preview_name)
    Row(
        modifier = modifier.fillMaxWidth().background(background).padding(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = null, tint = colors.onSurface)
        Avatar(photo = chat?.photo, name = name, id = chat?.id ?: 0L, size = MaterialTheme.sizes.avatarExtraSmall)
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.userContent,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
