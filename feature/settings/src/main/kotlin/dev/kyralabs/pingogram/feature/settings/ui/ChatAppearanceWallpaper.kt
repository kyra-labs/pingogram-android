package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import java.text.NumberFormat
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
            pending.blurred()?.let { blurred ->
                item { shapes ->
                    SwitchItem(
                        title = stringResource(R.string.settings_chat_wallpaper_blur),
                        summary = stringResource(R.string.settings_chat_wallpaper_blur_summary),
                        checked = blurred,
                        shapes = shapes,
                        onCheckedChange = actions.onBlurred,
                    )
                }
            }
            item { shapes ->
                SettingsSegment(shapes) {
                    PendingWallpaperActions(pending, picker.isWorking, actions.onCancel, actions.onApply)
                }
            }
        }
        if (set != null && picker.pending == null) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_chat_wallpaper_remove),
                    shapes = shapes,
                    supportingContent = { Text(stringResource(R.string.settings_chat_wallpaper_remove_summary)) },
                    onClick = actions.onRemove,
                )
            }
        }
        if (picker.forDarkTheme == true) {
            item { shapes ->
                StepSlider(
                    title = stringResource(R.string.settings_chat_wallpaper_dimming),
                    labels = dimmingLabels(),
                    index = state.appearance.wallpaperDimming / DIMMING_STEP,
                    shapes = shapes,
                    onStep = { actions.onDimming(it * DIMMING_STEP) },
                )
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

/** Whether the picked photo is blurred; null for a pattern, which has no blur. */
private fun WallpaperChoice.blurred(): Boolean? =
    when (this) {
        is WallpaperChoice.NewPhoto -> isBlurred
        is WallpaperChoice.Existing -> isBlurred.takeIf { wallpaper is Wallpaper.Photo }
    }

/** 0% to 100% in steps of [DIMMING_STEP], in the user's locale. */
@Composable
private fun dimmingLabels(): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val percent = NumberFormat.getPercentInstance(locale)
        (0..ChatAppearance.MAX_WALLPAPER_DIMMING step DIMMING_STEP).map { percent.format(it / PERCENT) }
    }
}

/** "Dark theme" for [dark], else "Light theme". */
private fun themeLabel(dark: Boolean): Int =
    if (dark) R.string.settings_chat_wallpaper_dark else R.string.settings_chat_wallpaper_light

/** Light theme, then dark theme: which the wallpaper is picked for. */
private val ThemeOptions = persistentListOf(false, true)

/** The dimming slider's step, in percent. */
private const val DIMMING_STEP = 10
private const val PERCENT = 100.0
