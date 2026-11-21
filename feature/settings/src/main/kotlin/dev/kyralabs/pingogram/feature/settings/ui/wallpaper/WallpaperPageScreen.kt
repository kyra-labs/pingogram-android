package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The wallpaper page for the account's wallpaper ([chatId] null) or chat [chatId]'s own: hoists
 * [WallpaperPageViewModel], opens Android's photo picker and shows in snackbars what failed.
 */
@Composable
internal fun WallpaperPageRoute(
    chatId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallpaperPageViewModel =
        hiltViewModel<WallpaperPageViewModel, WallpaperPageViewModel.Factory>(key = "wallpaper-$chatId") {
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
                is WallpaperPageEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    // The page opens on the theme it shows in; the account's page can switch to the other.
    val dark = isDarkTheme()
    LaunchedEffect(dark) { viewModel.onEvent(WallpaperPageEvent.Picker(WallpaperEvent.ShowTheme(dark))) }
    // Prepared at the screen's size, as it fills the screen behind the conversation in either orientation.
    val screenSidePx = resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            uri?.let {
                viewModel.onEvent(WallpaperPageEvent.Picker(WallpaperEvent.PickPhoto(it.toString(), screenSidePx)))
            }
        }
    PreviewSeedEffect(state, onSeed = { photo, seed -> viewModel.onEvent(WallpaperPageEvent.SeedFound(photo, seed)) })
    val colors = MaterialTheme.colorScheme
    // Other apps draw a pattern on its own fill: the theme's colors, as Pingogram draws it.
    val patternFill =
        remember(colors) { listOf(colors.primaryContainer, colors.tertiaryContainer).map { it.toArgb() and RGB } }
    WallpaperPageScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onApply = { forBoth -> viewModel.onEvent(WallpaperPageEvent.Apply(patternFill, forBoth)) },
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The wallpaper page (ADR 0041, `docs/DESIGN_SYSTEM.md` §6 "Wallpaper"): back, "Wallpaper" or "Wallpaper for <name>"
 * and Apply (disabled until something changed; for a chat it asks whom for, [WallpaperApplySheet]) in the top bar;
 * the large live preview ([WallpaperPagePreview]) and "Reset to default"; then a panel with the options
 * ([WallpaperOptions]) and the wallpapers' grid ([wallpaperGrid]), in one lazy grid.
 */
@Composable
internal fun WallpaperPageScreen(
    state: WallpaperPageUiState,
    onEvent: (WallpaperPageEvent) -> Unit,
    onPickPhoto: () -> Unit,
    onApply: (forBoth: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // A chat's Apply asks whom it is for first (WallpaperApplySheet); the account's applies at once.
    var choosing by rememberSaveable { mutableStateOf(false) }
    val title =
        when {
            !state.isChat -> stringResource(R.string.settings_wallpaper_title)
            state.chat == null -> stringResource(R.string.settings_chat_wallpaper_title)
            else -> stringResource(R.string.settings_wallpaper_title_chat, state.chat.title)
        }
    SettingsPageFrame(
        title = title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            Button(onClick = { if (state.isChat) choosing = true else onApply(false) }, enabled = state.canApply) {
                Text(stringResource(R.string.settings_chat_wallpaper_apply))
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()), Alignment.TopCenter) {
            WallpaperPageGrid(state, innerPadding, onEvent, onPickPhoto)
        }
    }
    if (choosing) {
        WallpaperApplySheet(
            chatName = state.chat?.title,
            canSetForBoth = state.canSetForBoth,
            onApply = { forBoth ->
                choosing = false
                onApply(forBoth)
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun WallpaperPageGrid(
    state: WallpaperPageUiState,
    innerPadding: PaddingValues,
    onEvent: (WallpaperPageEvent) -> Unit,
    onPickPhoto: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val panel = MaterialTheme.colorScheme.surfaceContainerLow
    val panelShape = MaterialTheme.shapes.extraLarge
    val layoutDirection = LocalLayoutDirection.current
    val margin = windowMargin()
    val spacing = MaterialTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell),
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxSize()
                .drawBehind {
                    panelTop(gridState)?.let { top ->
                        val outline =
                            panelShape.createOutline(
                                Size(size.width, size.height - top),
                                layoutDirection,
                                this,
                            )
                        translate(top = top) { drawOutline(outline, panel) }
                    }
                },
        state = gridState,
        contentPadding =
            PaddingValues(
                start = margin + innerPadding.calculateStartPadding(layoutDirection),
                end = margin + innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
    ) {
        item(key = "preview", span = { GridItemSpan(maxLineSpan) }, contentType = "preview") {
            Box(Modifier.fillMaxWidth().padding(vertical = spacing.small), Alignment.Center) {
                WallpaperPagePreview(state)
            }
        }
        item(key = "reset", span = { GridItemSpan(maxLineSpan) }, contentType = "reset") {
            Box(Modifier.fillMaxWidth().padding(bottom = spacing.large), Alignment.Center) {
                TextButton(onClick = { onEvent(WallpaperPageEvent.Reset) }, enabled = state.canReset) {
                    Icon(painterResource(UiR.drawable.ic_undo), contentDescription = null)
                    Text(
                        text = stringResource(R.string.settings_wallpaper_reset),
                        modifier = Modifier.padding(start = spacing.small),
                    )
                }
            }
        }
        item(key = PANEL_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "options") {
            WallpaperOptions(state, onEvent, onPickPhoto, Modifier.padding(top = spacing.large))
        }
        wallpaperGrid(
            state = state,
            onPick = { onEvent(WallpaperPageEvent.Picker(WallpaperEvent.Pick(it))) },
            onThemeColor = { onEvent(WallpaperPageEvent.PickThemeColor(it)) },
            onRetry = { onEvent(WallpaperPageEvent.Picker(WallpaperEvent.Retry)) },
        )
    }
}

/**
 * Where the panel behind the options and the grid starts, in the grid's pixels: the options' top while they show, 0
 * once they scrolled above it, and null while they are still below it.
 */
private fun panelTop(state: LazyGridState): Float? {
    val info = state.layoutInfo
    val options = info.visibleItemsInfo.firstOrNull { it.key == PANEL_KEY }
    return when {
        options != null -> (options.offset.y - info.viewportStartOffset).toFloat()
        (info.visibleItemsInfo.firstOrNull()?.index ?: 0) > PANEL_INDEX -> 0f
        else -> null
    }
}

private const val PANEL_KEY = "options"

/** The options' place in the grid: after the preview and "Reset to default". */
private const val PANEL_INDEX = 2

private const val RGB = 0xFFFFFF
