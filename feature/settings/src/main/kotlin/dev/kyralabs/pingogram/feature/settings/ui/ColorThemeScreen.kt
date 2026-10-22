package dev.kyralabs.pingogram.feature.settings.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import dev.kyralabs.pingogram.core.ui.theme.wallpaperColorScheme
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.launch

/**
 * The Theme page: hoists [ColorThemeViewModel], shows in snackbars a theme that could not be saved, and reads the
 * wallpaper's colors where Android has them (12+). [onBack] leaves the page.
 */
@Composable
internal fun ColorThemeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ColorThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is ColorThemeEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ColorThemeScreen(
        state = state,
        onSelect = viewModel::select,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        wallpaperSchemes = rememberWallpaperSchemes(),
    )
}

/**
 * The wallpaper's light and dark schemes for its chip, read again when the wallpaper changes (a configuration change);
 * null before Android 12, where the page offers no wallpaper colors.
 */
@Composable
private fun rememberWallpaperSchemes(): WallpaperSchemes? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { wallpaperSchemes(context) }
}

private fun wallpaperSchemes(context: Context): WallpaperSchemes? {
    val light = wallpaperColorScheme(context, dark = false)
    val dark = wallpaperColorScheme(context, dark = true)
    return if (light != null && dark != null) WallpaperSchemes(light, dark) else null
}

/** The wallpaper's schemes for its chip, light and dark. */
internal class WallpaperSchemes(
    val light: ColorScheme,
    val dark: ColorScheme,
)

/**
 * Settings → Appearance → Theme (`REQUIREMENTS.md` FR-S2, `docs/DESIGN_SYSTEM.md` §6 "Color themes",
 * `docs/adr/0016-color-themes.md`), as Pixel's Wallpaper & style: a live preview of the theme in use ([ThemePreview])
 * on top; then "Wallpaper colors" where [wallpaperSchemes] exist (Android 12+); then the generated themes, Pingogram's
 * first, as chips ([ThemeChip]) in a grid that wraps to the page's width, each in the light or dark scheme the app
 * shows now. Picking one calls [onSelect]; the app's theme, this page included, cross-fades to it at once. The chips
 * of each group are one radio group for screen readers.
 */
@Composable
internal fun ColorThemeScreen(
    state: ColorThemeUiState,
    onSelect: (ColorThemeOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    wallpaperSchemes: WallpaperSchemes? = null,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_color_theme),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            ColorThemeUiState.Loading -> {
                val loading = stringResource(R.string.settings_appearance_loading)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                }
            }

            is ColorThemeUiState.Content -> {
                ColorThemeContent(state.settings, onSelect, wallpaperSchemes)
            }
        }
    }
}

@Composable
private fun ColorThemeContent(
    settings: AppearanceSettings,
    onSelect: (ColorThemeOption) -> Unit,
    wallpaperSchemes: WallpaperSchemes?,
) {
    val selected = settings.colorTheme(wallpaperAvailable = wallpaperSchemes != null)
    val dark = settings.themeMode.isDark()
    SettingsGroup {
        item { shapes -> SettingsSegment(shapes) { ThemePreview(themeName = selected.name()) } }
    }
    if (wallpaperSchemes != null) {
        SettingsGroup(title = stringResource(R.string.settings_color_theme_section_wallpaper)) {
            item { shapes ->
                ChipGrid(shapes) {
                    ThemeChip(
                        scheme = if (dark) wallpaperSchemes.dark else wallpaperSchemes.light,
                        name = ColorThemeOption.Wallpaper.name(),
                        selected = selected == ColorThemeOption.Wallpaper,
                        onClick = { onSelect(ColorThemeOption.Wallpaper) },
                    )
                }
            }
        }
    }
    SettingsGroup(title = stringResource(R.string.settings_color_theme_section_basic)) {
        item { shapes ->
            ChipGrid(shapes) {
                ThemePalettes.forEach { palette ->
                    val option = ColorThemeOption.Palette(palette)
                    ThemeChip(
                        scheme = themeColorScheme(palette, dark, Contrast.Standard),
                        name = option.name(),
                        selected = selected == option,
                        onClick = { onSelect(option) },
                    )
                }
            }
        }
    }
}

/** The chips of a group in a segment, wrapping to its width, `spacing.medium` apart, one radio group. */
@Composable
private fun ChipGrid(
    shapes: ListItemShapes,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    SettingsSegment(shapes) {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) { content() }
    }
}

@PingogramPreviews
@Composable
private fun ColorThemeScreenPreview() {
    PingogramPreview {
        ColorThemeScreen(
            state = ColorThemeUiState.Content(AppearanceSettings(dynamicColor = false, palette = ThemePalette.Teal)),
            onSelect = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ColorThemeScreenDarkPreview() {
    PingogramPreview {
        ColorThemeScreen(
            state =
                ColorThemeUiState.Content(
                    AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColor = false, palette = ThemePalette.Pink),
                ),
            onSelect = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ColorThemeScreenLoadingPreview() {
    PingogramPreview { ColorThemeScreen(state = ColorThemeUiState.Loading, onSelect = {}, onBack = {}) }
}
