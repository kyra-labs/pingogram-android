package dev.kyralabs.pingogram.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.systemContrastOrNull
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.launch

/**
 * The Appearance page: hoists [AppearanceViewModel], shows in snackbars the changes that could not be saved, and tells
 * the page what this device supports: dynamic color from Android 12, and from Android 14 the system's contrast level,
 * which the theme follows instead of the app's. [onBack] leaves the page.
 */
@Composable
internal fun AppearanceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is AppearanceEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    AppearanceScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        systemContrast = systemContrastOrNull(),
    )
}

/**
 * Appearance (`REQUIREMENTS.md` FR-S2; `docs/DESIGN_SYSTEM.md` §2.1): the theme (system, light or dark, as
 * [SegmentedButton]s) and AMOLED black, then dynamic color (where [dynamicColorAvailable]) and the contrast level. With
 * a [systemContrast] (Android 14+) the contrast follows the system: its buttons show the system's level, disabled. The
 * app's theme follows each change at once, cross-fading its colors (`PingogramTheme`).
 */
@Composable
internal fun AppearanceScreen(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    dynamicColorAvailable: Boolean = true,
    systemContrast: Contrast? = null,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            AppearanceUiState.Loading -> {
                AppearanceLoading()
            }

            is AppearanceUiState.Content -> {
                AppearanceSettingsContent(
                    state.settings,
                    onEvent,
                    dynamicColorAvailable,
                    systemContrast,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceLoading() {
    val loading = stringResource(R.string.settings_appearance_loading)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

@Composable
private fun AppearanceSettingsContent(
    settings: AppearanceSettings,
    onEvent: (AppearanceEvent) -> Unit,
    dynamicColorAvailable: Boolean,
    systemContrast: Contrast?,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_theme)) {
        item { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = ThemeModes,
                    selected = settings.themeMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { onEvent(AppearanceEvent.SetThemeMode(it)) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.large),
                )
            }
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_amoled),
                summary = stringResource(R.string.settings_amoled_summary),
                checked = settings.amoled,
                shapes = shapes,
                onCheckedChange = { onEvent(AppearanceEvent.SetAmoled(it)) },
            )
        }
    }
    SettingsGroup(title = stringResource(R.string.settings_section_colors)) {
        if (dynamicColorAvailable) {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    shapes = shapes,
                    onCheckedChange = { onEvent(AppearanceEvent.SetDynamicColor(it)) },
                )
            }
        }
        item { shapes ->
            ContrastItem(
                contrast = systemContrast ?: settings.contrast,
                followsSystem = systemContrast != null,
                // Before Android 14 the wallpaper's colors have no contrast levels (rememberPingogramColorScheme).
                usesBrandColors =
                    dynamicColorAvailable && settings.dynamicColor && settings.contrast != Contrast.Standard,
                shapes = shapes,
                onSelect = { onEvent(AppearanceEvent.SetContrast(it)) },
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun AppearanceScreenPreview() {
    PingogramPreview {
        AppearanceScreen(state = AppearanceUiState.Content(AppearanceSettings()), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun AppearanceScreenContrastFollowsSystemPreview() {
    PingogramPreview {
        AppearanceScreen(
            state = AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)),
            onEvent = {},
            onBack = {},
            systemContrast = Contrast.Medium,
        )
    }
}

@PingogramPreviews
@Composable
private fun AppearanceScreenWithoutDynamicColorPreview() {
    PingogramPreview {
        AppearanceScreen(
            state = AppearanceUiState.Content(AppearanceSettings(dynamicColor = false, contrast = Contrast.High)),
            onEvent = {},
            onBack = {},
            dynamicColorAvailable = false,
        )
    }
}

@PingogramPreviews
@Composable
private fun AppearanceScreenLoadingPreview() {
    PingogramPreview { AppearanceScreen(state = AppearanceUiState.Loading, onEvent = {}, onBack = {}) }
}
