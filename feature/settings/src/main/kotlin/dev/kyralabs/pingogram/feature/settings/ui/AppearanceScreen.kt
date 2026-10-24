package dev.kyralabs.pingogram.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.systemContrastOrNull
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The Appearance page: hoists [AppearanceViewModel], shows in snackbars the changes that could not be saved, and tells
 * the page what this device supports: wallpaper colors from Android 12, and from Android 14 the system's contrast
 * level, which the theme follows instead of the app's. [onOpenColorTheme] opens the Theme page,
 * [onOpenChatAppearance] the Chat appearance page, [onOpenQuickReaction] Double-tap to react; [onBack] leaves.
 */
@Composable
internal fun AppearanceRoute(
    onOpenColorTheme: () -> Unit,
    onOpenChatAppearance: () -> Unit,
    onOpenQuickReaction: () -> Unit,
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
        onOpenColorTheme = onOpenColorTheme,
        onOpenChatAppearance = onOpenChatAppearance,
        onOpenQuickReaction = onOpenQuickReaction,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        systemContrast = systemContrastOrNull(),
    )
}

/**
 * Appearance (`REQUIREMENTS.md` FR-S2; `docs/DESIGN_SYSTEM.md` §2.1): light or dark (system, light or dark, as a
 * connected button group) and AMOLED black, then the color theme, a row that opens the Theme page with the theme's
 * chip at its end ([onOpenColorTheme]: the wallpaper's colors where [dynamicColorAvailable], or a generated theme), and
 * the contrast level; then under "Chat list" whether story rings show above the chats; then under "Chats" a row that
 * opens the Chat appearance page ([onOpenChatAppearance]: message text size, bubble corners, wallpaper) with their
 * summary, a row that opens Double-tap to react ([onOpenQuickReaction]), and which Enter sends on a hardware and on
 * the on-screen keyboard.
 * With a [systemContrast] (Android 14+) the contrast follows the system: its buttons show the system's level, disabled.
 * The app's theme follows each change at once, cross-fading its colors (`PingogramTheme`).
 */
@Composable
internal fun AppearanceScreen(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenColorTheme: () -> Unit = {},
    onOpenChatAppearance: () -> Unit = {},
    onOpenQuickReaction: () -> Unit = {},
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
                    ChatsSettings(state.sendKeys, state.raiseToListen, state.chatList),
                    onEvent,
                    AppearancePages(onOpenColorTheme, onOpenChatAppearance, onOpenQuickReaction),
                    dynamicColorAvailable,
                    systemContrast,
                )
            }
        }
    }
}

@Composable
private fun AppearanceLoading() {
    val loading = stringResource(R.string.settings_appearance_loading)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

@Suppress("LongParameterList") // The settings, the chats' settings, the events, the pages and what the device offers.
@Composable
private fun AppearanceSettingsContent(
    settings: AppearanceSettings,
    chats: ChatsSettings,
    onEvent: (AppearanceEvent) -> Unit,
    pages: AppearancePages,
    dynamicColorAvailable: Boolean,
    systemContrast: Contrast?,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_light_dark)) {
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
        item { shapes -> ColorThemeItem(settings, dynamicColorAvailable, shapes, pages.onOpenColorTheme) }
        item { shapes ->
            ContrastItem(
                contrast = systemContrast ?: settings.contrast,
                followsSystem = systemContrast != null,
                // Before Android 14 the wallpaper's colors have no contrast levels (rememberPingogramColorScheme).
                fallbackTheme =
                    ColorThemeOption.Palette(settings.palette).takeIf {
                        dynamicColorAvailable && settings.dynamicColor && settings.contrast != Contrast.Standard
                    },
                shapes = shapes,
                onSelect = { onEvent(AppearanceEvent.SetContrast(it)) },
            )
        }
    }
    ChatListGroup(chats.chatList, onEvent)
    ChatsGroup(settings.chat, chats, onEvent, pages.onOpenChatAppearance, pages.onOpenQuickReaction)
}

/** What opens the pages above Appearance. */
private class AppearancePages(
    val onOpenColorTheme: () -> Unit,
    val onOpenChatAppearance: () -> Unit,
    val onOpenQuickReaction: () -> Unit,
)

@PingogramPreviews
@Composable
private fun AppearanceScreenPreview() {
    PingogramPreview {
        AppearanceScreen(state = AppearanceUiState.Content(AppearanceSettings()), onEvent = {}, onBack = {})
    }
}

/**
 * The row that opens the Theme page, one row of its group ([shapes]): "Theme" over the theme's name, with its chip
 * ([ThemeChip], `sizes.leadingIconContainer`) at its end, in the scheme the app shows now.
 */
@Composable
private fun ColorThemeItem(
    settings: AppearanceSettings,
    dynamicColorAvailable: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val theme = settings.colorTheme(wallpaperAvailable = dynamicColorAvailable)
    SettingsRow(
        title = stringResource(R.string.settings_color_theme),
        shapes = shapes,
        supportingContent = { Text(theme.name()) },
        trailingContent = {
            ThemeChip(
                scheme = MaterialTheme.colorScheme,
                name = theme.name(),
                selected = false,
                size = MaterialTheme.sizes.leadingIconContainer,
            )
        },
        onClick = onClick,
    )
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
