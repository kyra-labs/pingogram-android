package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.brush
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The account's wallpapers in Chat appearance's "Wallpaper" group (docs/DESIGN_SYSTEM.md §6 "Chat appearance").

/**
 * The installed wallpapers after the theme-color chips: "From your photos" (Android's photo picker, [onPickPhoto]),
 * then each of the account's installed photos and patterns ([installed]) as a chip, the one with [selectedId]
 * selected. While they load, nothing more; when they cannot load, "Try again".
 */
@Composable
internal fun AccountWallpaperChips(
    installed: InstalledWallpapers,
    selectedId: Long?,
    onPick: (Wallpaper) -> Unit,
    onPickPhoto: () -> Unit,
    onRetry: () -> Unit,
) {
    PhotoChip(onPickPhoto)
    // While they load, nothing more shows.
    if (installed is InstalledWallpapers.Failed) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_chat_wallpaper_installed_failed)) }
    }
    if (installed is InstalledWallpapers.Loaded) {
        installed.wallpapers.forEachIndexed { index, wallpaper ->
            AccountWallpaperChip(wallpaper, index + 1, selected = wallpaper.id == selectedId) { onPick(wallpaper) }
        }
    }
}

/** "From your photos": a chip of the theme chips' size with the photo library symbol; a button for screen readers. */
@Composable
private fun PhotoChip(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(R.string.settings_chat_wallpaper_photo)
    TextButton(
        onClick = onClick,
        modifier = Modifier.size(MaterialTheme.sizes.themeChip).semantics { contentDescription = name },
        shape = CircleShape,
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
            ),
    ) {
        Icon(painterResource(UiR.drawable.ic_photo_library_filled), contentDescription = null)
    }
}

/**
 * One installed wallpaper as a chip: its thumbnail cropped to the chip, a pattern tinted in `onSurface` over the
 * theme's colors as the conversation draws it; while [selected], `Cookie9Sided` as the theme chips. Screen readers hear
 * a radio button named "Photo wallpaper 3" or "Pattern 3" ([number]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountWallpaperChip(
    wallpaper: Wallpaper,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = selected)
    val name =
        when (wallpaper) {
            is Wallpaper.Pattern -> stringResource(R.string.settings_chat_wallpaper_pattern, number)
            else -> stringResource(R.string.settings_chat_wallpaper_photo_number, number)
        }
    val image = (wallpaper as? Wallpaper.Photo)?.image ?: (wallpaper as? Wallpaper.Pattern)?.image
    val patternInk = ColorFilter.tint(colors.onSurface.copy(alpha = PATTERN_ALPHA))
    val fill =
        MaterialTheme.chatStyle.wallpaper ?: ChatWallpaper.PrimaryToTertiary.brush(colors) ?: SolidColor(colors.surface)
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.themeChip)
                .selectable(selected = selected, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics { contentDescription = name }
                .clip(shape)
                .background(if (wallpaper is Wallpaper.Pattern) fill else SolidColor(colors.surfaceContainerHighest)),
    ) {
        image?.let {
            AsyncImage(
                model = TdlibFileImage((it.thumbnail ?: it.file).id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = patternInk.takeIf { wallpaper is Wallpaper.Pattern },
                modifier = Modifier.size(MaterialTheme.sizes.themeChip),
            )
        }
    }
}

/**
 * What the picked wallpaper needs before it is applied: for a photo from the phone, that it is uploaded to Telegram
 * and so shows in the user's other apps too; then Cancel and Apply, disabled while [working].
 */
@Composable
internal fun PendingWallpaperActions(
    pending: WallpaperChoice,
    working: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    if (pending is WallpaperChoice.NewPhoto) {
        Text(
            text = stringResource(R.string.settings_chat_wallpaper_upload_note),
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
    ) {
        TextButton(onClick = onCancel, enabled = !working) {
            Text(stringResource(R.string.settings_chat_wallpaper_cancel))
        }
        Button(onClick = onApply, enabled = !working) { Text(stringResource(R.string.settings_chat_wallpaper_apply)) }
    }
}

/** The id of the wallpaper the chips show as chosen: the one picked, else the account's for the theme picked for. */
internal fun selectedWallpaperId(
    pending: WallpaperChoice?,
    set: Wallpaper?,
): Long? =
    when (pending) {
        is WallpaperChoice.Existing -> pending.wallpaper.id
        is WallpaperChoice.NewPhoto -> null
        null -> set?.id
    }

/** How strongly a pattern's chip shows it over the theme's colors, as an intensity of 50 does in a conversation. */
private const val PATTERN_ALPHA = 0.5f
