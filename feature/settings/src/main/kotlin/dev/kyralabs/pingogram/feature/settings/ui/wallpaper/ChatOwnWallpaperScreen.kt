package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChatPreview
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/** Chat [chatId]'s own wallpaper page: hoists [ChatOwnWallpaperViewModel] and shows in snackbars what failed. */
@Composable
internal fun ChatOwnWallpaperRoute(
    chatId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatOwnWallpaperViewModel =
        hiltViewModel<ChatOwnWallpaperViewModel, ChatOwnWallpaperViewModel.Factory>(key = "chat-wallpaper-$chatId") {
            it.create(chatId)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatOwnWallpaperEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    // The installed wallpapers suggested for the theme the page shows in; a chat has one wallpaper for both.
    val dark = isDarkTheme()
    LaunchedEffect(dark) { viewModel.onWallpaperEvent(WallpaperEvent.ShowTheme(dark)) }
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            uri?.let { viewModel.onWallpaperEvent(WallpaperEvent.PickPhoto(it.toString())) }
        }
    val colors = MaterialTheme.colorScheme
    val actions =
        remember(viewModel, photoPicker, colors) {
            WallpaperActions(
                onPick = { viewModel.onWallpaperEvent(WallpaperEvent.Pick(it)) },
                onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                onApply = {
                    val fill = listOf(colors.primaryContainer, colors.tertiaryContainer).map { it.toArgb() and RGB }
                    viewModel.onWallpaperEvent(WallpaperEvent.Apply(fill))
                },
                onCancel = { viewModel.onWallpaperEvent(WallpaperEvent.Cancel) },
                onRetry = { viewModel.onWallpaperEvent(WallpaperEvent.Retry) },
            )
        }
    ChatOwnWallpaperScreen(
        state = state,
        actions = actions,
        onForBoth = { viewModel.onEvent(ChatOwnWallpaperEvent.SetForBoth(it)) },
        onReset = { viewModel.onEvent(ChatOwnWallpaperEvent.Reset) },
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * "Wallpaper for this chat" (owner decision 2026-11-20, `docs/DESIGN_SYSTEM.md` §6): the sample conversation on the
 * chat's own wallpaper, or on the one picked; the account's installed wallpapers and "From your photos"; with Telegram
 * Premium, whether it is for both people; then Cancel and Apply for a pick, or "Reset" for the chat's own.
 */
@Composable
internal fun ChatOwnWallpaperScreen(
    state: ChatOwnWallpaperUiState,
    actions: WallpaperActions,
    onForBoth: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val picker = state.picker
    SettingsPageScaffold(
        title = stringResource(R.string.settings_chat_wallpaper_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        SettingsGroup {
            item { shapes ->
                SettingsSegment(shapes) {
                    Box(Modifier.clip(shapes.shape)) {
                        PreviewWallpaper(
                            picker.pending,
                            state.own?.wallpaper,
                            state.dimming,
                            Modifier.matchParentSize(),
                        )
                        ChatPreview(drawsWallpaper = false)
                    }
                }
            }
        }
        ChatOwnWallpaperGroup(state, actions, onForBoth, onReset)
    }
}

/**
 * The installed wallpapers and the photo picker; with Telegram Premium, whether the wallpaper is for both people; then
 * Cancel and Apply for a pick, or "Reset wallpaper" for the chat's own.
 */
@Composable
private fun ChatOwnWallpaperGroup(
    state: ChatOwnWallpaperUiState,
    actions: WallpaperActions,
    onForBoth: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val picker = state.picker
    SettingsGroup(footer = stringResource(R.string.settings_chat_wallpaper_own_footer)) {
        item { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup().padding(MaterialTheme.spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    AccountWallpaperChips(
                        installed = picker.installed,
                        selectedId = selectedWallpaperId(picker.pending, state.own?.wallpaper),
                        onPick = actions.onPick,
                        onPickPhoto = actions.onPickPhoto,
                        onRetry = actions.onRetry,
                    )
                }
            }
        }
        if (state.canSetForBoth) {
            item { shapes ->
                SettingsSegment(shapes) {
                    ChoiceRow(
                        options = ForBothOptions,
                        selected = state.forBoth,
                        label = { stringResource(forBothLabel(it)) },
                        onSelect = onForBoth,
                        contentPadding = PaddingValues(MaterialTheme.spacing.large),
                    )
                }
            }
        }
        picker.pending?.let { pending ->
            item { shapes ->
                SettingsSegment(shapes) {
                    PendingWallpaperActions(pending, picker.isWorking, actions.onCancel, actions.onApply)
                }
            }
        }
        if (state.own != null && picker.pending == null) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_chat_wallpaper_reset),
                    shapes = shapes,
                    supportingContent = { Text(stringResource(R.string.settings_chat_wallpaper_reset_summary)) },
                    onClick = onReset,
                )
            }
        }
    }
}

/** "For both of you" for [forBoth], else "Only for me". */
private fun forBothLabel(forBoth: Boolean): Int =
    if (forBoth) R.string.settings_chat_wallpaper_for_both else R.string.settings_chat_wallpaper_for_me

/** Only for me, then for both people. */
private val ForBothOptions = persistentListOf(false, true)

private const val RGB = 0xFFFFFF

@PingogramPreviews
@Composable
private fun ChatOwnWallpaperScreenPreview() {
    PingogramPreview {
        ChatOwnWallpaperScreen(
            state = ChatOwnWallpaperUiState(canSetForBoth = true),
            actions = WallpaperActions(),
            onForBoth = {},
            onReset = {},
            onBack = {},
        )
    }
}
