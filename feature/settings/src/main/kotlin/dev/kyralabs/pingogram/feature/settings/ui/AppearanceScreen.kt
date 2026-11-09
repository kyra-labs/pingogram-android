package dev.kyralabs.pingogram.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
 * [onOpenChatAppearance] the Chat appearance page, [onOpenQuickReaction] Double-tap to react, [onOpenSwipeActions]
 * Swipe actions, [onOpenShownFirst] Chats shown first; [onBack] leaves.
 */
@Suppress("LongParameterList") // The five pages it opens, back, the modifier and the view model.
@Composable
internal fun AppearanceRoute(
    onOpenColorTheme: () -> Unit,
    onOpenChatAppearance: () -> Unit,
    onOpenQuickReaction: () -> Unit,
    onOpenSwipeActions: () -> Unit,
    onOpenShownFirst: () -> Unit,
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

                AppearanceEffect.ResetDone -> {
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_appearance_reset_done),
                                actionLabel = resources.getString(R.string.settings_appearance_reset_undo),
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(AppearanceEvent.UndoReset)
                    }
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
        onOpenSwipeActions = onOpenSwipeActions,
        onOpenShownFirst = onOpenShownFirst,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        systemContrast = systemContrastOrNull(),
        wallpaperSchemes = rememberWallpaperSchemes(),
    )
}

/**
 * Appearance (`REQUIREMENTS.md` FR-S2; `docs/DESIGN_SYSTEM.md` §13.10): [AppearanceContent], with "Reset" at the top
 * bar's end. The color themes' row opens the Theme page ([onOpenColorTheme]), the wallpaper's colors first where
 * [wallpaperSchemes] has them; "Chat list" opens Swipe actions ([onOpenSwipeActions]); "Chats" opens Chat appearance
 * ([onOpenChatAppearance]), Double-tap to react ([onOpenQuickReaction]) and Chats shown first ([onOpenShownFirst]).
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
    onOpenSwipeActions: () -> Unit = {},
    onOpenShownFirst: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    systemContrast: Contrast? = null,
    wallpaperSchemes: WallpaperSchemes? = null,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state is AppearanceUiState.Content) {
                ResetAppearanceButton { onEvent(AppearanceEvent.Reset) }
            }
        },
    ) {
        when (state) {
            AppearanceUiState.Loading -> {
                AppearanceLoading()
            }

            is AppearanceUiState.Content -> {
                val chats = ChatsSettings(state.sendKeys, state.raiseToListen, state.chatList)
                val pages =
                    AppearancePages(
                        onOpenColorTheme,
                        onOpenChatAppearance,
                        onOpenQuickReaction,
                        onOpenSwipeActions,
                        onOpenShownFirst,
                    )
                AppearanceContent(state.settings, chats, onEvent, pages, wallpaperSchemes, systemContrast)
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

/** What opens the pages above Appearance. */
internal class AppearancePages(
    val onOpenColorTheme: () -> Unit,
    val onOpenChatAppearance: () -> Unit,
    val onOpenQuickReaction: () -> Unit,
    val onOpenSwipeActions: () -> Unit,
    val onOpenShownFirst: () -> Unit,
)

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
        )
    }
}

@PingogramPreviews
@Composable
private fun AppearanceScreenLoadingPreview() {
    PingogramPreview { AppearanceScreen(state = AppearanceUiState.Loading, onEvent = {}, onBack = {}) }
}
