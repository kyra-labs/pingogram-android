package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.feature.settings.R
import java.text.NumberFormat

/**
 * Chat appearance's "Wallpaper" (ADR 0041): one row, what the conversation shows in its summary (the account's
 * wallpaper for the theme the page shows in, else the theme-color one), opening the wallpaper page ([onOpen]).
 */
@Composable
internal fun WallpaperGroup(
    themeColor: ChatWallpaper,
    set: Wallpaper?,
    onOpen: () -> Unit,
) {
    SettingsGroup(footer = stringResource(R.string.settings_chat_wallpaper_footer)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_wallpaper_title),
                shapes = shapes,
                supportingContent = { Text(wallpaperSummary(themeColor, set)) },
                onClick = onOpen,
            )
        }
    }
}

/** What the conversation shows: a photo, a pattern or colors from Telegram, else the theme-color wallpaper's name. */
@Composable
private fun wallpaperSummary(
    themeColor: ChatWallpaper,
    set: Wallpaper?,
): String =
    when (set) {
        is Wallpaper.Photo -> stringResource(R.string.settings_wallpaper_summary_photo)
        is Wallpaper.Pattern -> stringResource(R.string.settings_wallpaper_summary_pattern)
        is Wallpaper.Fill -> stringResource(R.string.settings_wallpaper_summary_fill)
        null -> themeColor.label()
    }

/**
 * "Dimming in dark theme": a slider from 0% to 100% in steps of [DIMMING_STEP] at [percent] (a value set elsewhere on
 * its nearest step), telling [onDimming] each step dragged to and [onSettled] once the finger lets go. The
 * wallpaper page's options use it.
 */
@Composable
internal fun WallpaperDimmingSlider(
    percent: Int,
    shapes: ListItemShapes,
    onDimming: (percent: Int) -> Unit,
    onSettled: () -> Unit = {},
) {
    StepSlider(
        title = stringResource(R.string.settings_chat_wallpaper_dimming),
        labels = dimmingLabels(),
        index = ((percent + DIMMING_STEP / 2) / DIMMING_STEP).coerceIn(0, MAX_DIMMING_STEP),
        shapes = shapes,
        onStep = { onDimming(it * DIMMING_STEP) },
        onSettled = onSettled,
    )
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

/** The dimming slider's step, in percent. */
private const val DIMMING_STEP = 10
private const val MAX_DIMMING_STEP = ChatAppearance.MAX_WALLPAPER_DIMMING / DIMMING_STEP
private const val PERCENT = 100.0
