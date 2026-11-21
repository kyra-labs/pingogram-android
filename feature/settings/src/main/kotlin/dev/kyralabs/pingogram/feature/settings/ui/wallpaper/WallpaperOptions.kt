package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.WallpaperDimmingSlider
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The options above the wallpapers' grid, one segmented group: on the account's page, which theme it is picked for
 * ("Light theme" and "Dark theme"); for dark theme, "Dimming in dark theme"; for a photo, "Blur"; then "Choose a
 * photo" (Android's photo picker, no permission), and for a photo from the phone that it is uploaded to Telegram.
 */
@Composable
internal fun WallpaperOptions(
    state: WallpaperPageUiState,
    onEvent: (WallpaperPageEvent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val forDark = state.picker.forDarkTheme
    val blurred = state.blurred()
    val rows =
        listOfNotNull(
            Option.Theme.takeIf { !state.isChat && forDark != null },
            Option.Dimming.takeIf { forDark == true },
            Option.Blur.takeIf { blurred != null },
        )
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        rows.forEachIndexed { index, option ->
            val shapes = ListItemDefaults.segmentedShapes(index, rows.size)
            OptionRow(option, state, shapes, onEvent)
        }
        FilledTonalButton(
            onClick = onPickPhoto,
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.medium),
        ) {
            Icon(painterResource(UiR.drawable.ic_photo_library_filled), contentDescription = null)
            Text(
                text = stringResource(R.string.settings_wallpaper_choose_photo),
                modifier = Modifier.padding(start = spacing.small),
            )
        }
        if (state.picker.pending is WallpaperChoice.NewPhoto) {
            Text(
                text = stringResource(R.string.settings_chat_wallpaper_upload_note),
                modifier = Modifier.padding(horizontal = spacing.small),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One row of [WallpaperOptions], [shapes] its place in the group. */
@Composable
private fun OptionRow(
    option: Option,
    state: WallpaperPageUiState,
    shapes: ListItemShapes,
    onEvent: (WallpaperPageEvent) -> Unit,
) {
    when (option) {
        Option.Theme -> {
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = ThemeOptions,
                    selected = state.picker.forDarkTheme == true,
                    label = { stringResource(themeLabel(it)) },
                    onSelect = { onEvent(WallpaperPageEvent.Picker(WallpaperEvent.ShowTheme(it))) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.large),
                )
            }
        }

        Option.Dimming -> {
            WallpaperDimmingSlider(state.dimming, shapes, { onEvent(WallpaperPageEvent.SetDimming(it)) })
        }

        Option.Blur -> {
            SwitchItem(
                title = stringResource(R.string.settings_chat_wallpaper_blur),
                summary = stringResource(R.string.settings_chat_wallpaper_blur_summary),
                checked = state.blurred() == true,
                shapes = shapes,
                onCheckedChange = { onEvent(WallpaperPageEvent.Picker(WallpaperEvent.SetBlurred(it))) },
            )
        }
    }
}

/** Whether the photo picked, else the photo shown, is blurred; null for anything else, which has no blur. */
private fun WallpaperPageUiState.blurred(): Boolean? =
    when (val pending = picker.pending) {
        is WallpaperChoice.NewPhoto -> pending.isBlurred
        is WallpaperChoice.Existing -> pending.isBlurred.takeIf { pending.wallpaper is Wallpaper.Photo }
        null -> (shownWallpaper as? Wallpaper.Photo)?.isBlurred
    }

/** The rows the options can have, in their order. */
private enum class Option { Theme, Dimming, Blur }

/** "Dark theme" for [dark], else "Light theme". */
private fun themeLabel(dark: Boolean): Int =
    if (dark) R.string.settings_chat_wallpaper_dark else R.string.settings_chat_wallpaper_light

/** Light theme, then dark theme: which the account's wallpaper is picked for. */
private val ThemeOptions = persistentListOf(false, true)

@PingogramPreviews
@Composable
private fun WallpaperOptionsPreview() {
    PingogramPreview {
        WallpaperOptions(
            state = WallpaperPageUiState(isChat = false, picker = WallpaperPickerState(forDarkTheme = true)),
            onEvent = {},
            onPickPhoto = {},
        )
    }
}
