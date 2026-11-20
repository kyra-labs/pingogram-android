package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.AccountWallpaperChips
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.PendingWallpaperActions
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperActions
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.selectedWallpaperId
import kotlinx.collections.immutable.persistentListOf

/**
 * Chat appearance's "Wallpaper" (owner decision 2026-11-20, `docs/DESIGN_SYSTEM.md` §6): which theme it is picked
 * for, then the chips ([WallpaperChips]); a pick waits for Cancel or Apply.
 */
@Composable
internal fun WallpaperGroup(
    state: ChatAppearanceUiState.Content,
    set: Wallpaper?,
    onWallpaper: (ChatWallpaper) -> Unit,
    actions: WallpaperActions,
) {
    val picker = state.picker
    SettingsGroup(
        title = stringResource(R.string.settings_chat_section_wallpaper),
        footer = stringResource(R.string.settings_chat_wallpaper_footer),
    ) {
        picker.forDarkTheme?.let { forDark ->
            item { shapes ->
                SettingsSegment(shapes) {
                    ChoiceRow(
                        options = ThemeOptions,
                        selected = forDark,
                        label = { stringResource(themeLabel(it)) },
                        onSelect = actions.onTheme,
                        contentPadding = PaddingValues(MaterialTheme.spacing.large),
                    )
                }
            }
        }
        item { shapes -> SettingsSegment(shapes) { WallpaperChips(state, set, onWallpaper, actions) } }
        picker.pending?.let { pending ->
            item { shapes ->
                SettingsSegment(shapes) {
                    PendingWallpaperActions(pending, picker.isWorking, actions.onCancel, actions.onApply)
                }
            }
        }
    }
}

/**
 * The theme-color chips, chosen only where the account has no wallpaper for the theme ([set]) and nothing is picked,
 * then the account's wallpapers ([AccountWallpaperChips]); one radio group for screen readers.
 */
@Composable
private fun WallpaperChips(
    state: ChatAppearanceUiState.Content,
    set: Wallpaper?,
    onWallpaper: (ChatWallpaper) -> Unit,
    actions: WallpaperActions,
) {
    val picker = state.picker
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        val themeColorShown = set == null && picker.pending == null
        ChatWallpaper.entries.forEach { wallpaper ->
            WallpaperChip(
                wallpaper = wallpaper,
                selected = themeColorShown && wallpaper == state.appearance.wallpaper,
                onClick = { onWallpaper(wallpaper) },
            )
        }
        AccountWallpaperChips(
            installed = picker.installed,
            selectedId = selectedWallpaperId(picker.pending, set),
            onPick = actions.onPick,
            onPickPhoto = actions.onPickPhoto,
            onRetry = actions.onRetry,
        )
    }
}

/** "Dark theme" for [dark], else "Light theme". */
private fun themeLabel(dark: Boolean): Int =
    if (dark) R.string.settings_chat_wallpaper_dark else R.string.settings_chat_wallpaper_light

/** Light theme, then dark theme: which the wallpaper is picked for. */
private val ThemeOptions = persistentListOf(false, true)
