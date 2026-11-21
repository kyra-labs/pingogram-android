package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.brush
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.popIn
import dev.kyralabs.pingogram.core.ui.theme.popOut
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.label
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The wallpaper page's grid (ADR 0041): on the account's page the theme-color wallpapers first, then Telegram's
 * wallpapers and patterns installed on the account, as rounded thumbnails; the one shown has a check. While they load,
 * an indicator; when they cannot, "Try again". Each thumbnail is a radio button for screen readers.
 */
internal fun LazyGridScope.wallpaperGrid(
    state: WallpaperPageUiState,
    onPick: (Wallpaper) -> Unit,
    onThemeColor: (ChatWallpaper) -> Unit,
    onRetry: () -> Unit,
) {
    val picker = state.picker
    val themeColorShown = picker.pending == null && state.shownWallpaper == null
    if (!state.isChat) {
        items(ChatWallpaper.entries, key = { "color-${it.name}" }, contentType = { "color" }) { wallpaper ->
            ThemeColorThumbnail(wallpaper, themeColorShown && wallpaper == state.shownThemeColor) {
                onThemeColor(wallpaper)
            }
        }
    }
    when (val installed = picker.installed) {
        InstalledWallpapers.Loading -> {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
                Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            }
        }

        InstalledWallpapers.Failed -> {
            item(key = "failed", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
                TextButton(
                    onClick = onRetry,
                ) { Text(stringResource(R.string.settings_chat_wallpaper_installed_failed)) }
            }
        }

        is InstalledWallpapers.Loaded -> {
            val selectedId = selectedWallpaperId(picker.pending, state.shownWallpaper)
            installedItems(installed, selectedId, onPick)
        }
    }
}

/** Each installed wallpaper, keyed by its id, numbered for screen readers in TDLib's order. */
private fun LazyGridScope.installedItems(
    installed: InstalledWallpapers.Loaded,
    selectedId: Long?,
    onPick: (Wallpaper) -> Unit,
) {
    installed.wallpapers.forEachIndexed { index, wallpaper ->
        item(key = wallpaper.id, contentType = "wallpaper") {
            WallpaperThumbnail(wallpaper, index + 1, wallpaper.id == selectedId) { onPick(wallpaper) }
        }
    }
}

/**
 * One of Telegram's wallpapers: its thumbnail cropped to the cell, a pattern tinted in `onSurface` over the theme's
 * colors as the conversation draws it. Screen readers hear "Photo wallpaper 3" or "Pattern 3" ([number]).
 */
@Composable
private fun WallpaperThumbnail(
    wallpaper: Wallpaper,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isPattern = wallpaper is Wallpaper.Pattern
    val name =
        if (isPattern) {
            stringResource(R.string.settings_chat_wallpaper_pattern, number)
        } else {
            stringResource(R.string.settings_chat_wallpaper_photo_number, number)
        }
    val fill =
        if (isPattern) {
            MaterialTheme.chatStyle.wallpaper ?: ChatWallpaper.PrimaryToTertiary.brush(colors)
        } else {
            null
        }
    val image = (wallpaper as? Wallpaper.Photo)?.image ?: (wallpaper as? Wallpaper.Pattern)?.image
    Thumbnail(name, selected, fill ?: SolidColor(colors.surfaceContainerHighest), onClick) {
        image?.let {
            AsyncImage(
                model = TdlibFileImage((it.thumbnail ?: it.file).id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(colors.onSurface.copy(alpha = PATTERN_ALPHA)).takeIf { isPattern },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A theme-color wallpaper, filled as the theme draws it ("No wallpaper" the conversation's `surface`). */
@Composable
private fun ThemeColorThumbnail(
    wallpaper: ChatWallpaper,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Thumbnail(wallpaper.label(), selected, wallpaper.brush(colors) ?: SolidColor(colors.surface), onClick) {}
}

/**
 * A cell of the grid: [background] in a rounded rectangle shaped like a phone's screen. While [selected], an outline
 * in `primary` and a check on `primaryContainer` that pops in on the fast springs; a pick ticks (`SegmentTick`).
 */
@Composable
private fun Thumbnail(
    name: String,
    selected: Boolean,
    background: Brush,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val haptics = LocalHapticFeedback.current
    val outline = if (selected) colors.primary else colors.outlineVariant
    Box(
        modifier =
            Modifier
                .padding(MaterialTheme.spacing.extraSmall)
                .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                .clip(shape)
                .selectable(selected = selected, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics { contentDescription = name }
                .background(background)
                .border(MaterialTheme.sizes.selectedCardOutline, outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        content()
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(selected, enter = popIn(motion), exit = popOut(motion)) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.padding(MaterialTheme.spacing.small).size(IconButtonDefaults.smallIconSize),
                )
            }
        }
    }
}

/** A thumbnail's width to its height, a little less tall than a phone's screen so more of them fit. */
private const val THUMBNAIL_ASPECT_RATIO = 3f / 4f

/** How strongly a pattern's thumbnail shows it over the theme's colors, as an intensity of 50 does. */
private const val PATTERN_ALPHA = 0.5f

@PingogramPreviews
@Composable
private fun WallpaperGridPreview() {
    PingogramPreview {
        LazyVerticalGrid(columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell)) {
            wallpaperGrid(
                state =
                    WallpaperPageUiState(
                        isChat = false,
                        saved = SavedWallpaper(themeColor = ChatWallpaper.Primary),
                    ),
                onPick = {},
                onThemeColor = {},
                onRetry = {},
            )
        }
    }
}
