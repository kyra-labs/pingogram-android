package dev.kyralabs.pingogram.feature.settings.ui

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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.AccountWallpaperChips
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.PendingWallpaperActions
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.PreviewWallpaper
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperActions
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperEvent
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.selectedWallpaperId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The Chat appearance page: hoists [ChatAppearanceViewModel] and shows in snackbars what could not be saved. */
@Composable
internal fun ChatAppearanceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatAppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is ChatAppearanceEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    // Android's photo picker: no permission, and only the photo the user picks reaches the app. The photo is prepared
    // at the screen's size, as it fills the screen behind a conversation in either orientation.
    val screenSidePx = resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            uri?.let { viewModel.onWallpaperEvent(WallpaperEvent.PickPhoto(it.toString(), screenSidePx)) }
        }
    val colors = MaterialTheme.colorScheme
    val wallpaperActions =
        remember(viewModel, photoPicker, colors) {
            WallpaperActions(
                onTheme = { viewModel.onWallpaperEvent(WallpaperEvent.ShowTheme(it)) },
                onPick = { viewModel.onWallpaperEvent(WallpaperEvent.Pick(it)) },
                onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                // Other apps draw a pattern on its own fill: the theme's colors, as Pingogram draws it.
                onApply = { viewModel.onWallpaperEvent(WallpaperEvent.Apply(patternFill(colors))) },
                onCancel = { viewModel.onWallpaperEvent(WallpaperEvent.Cancel) },
                onRetry = { viewModel.onWallpaperEvent(WallpaperEvent.Retry) },
                onBlurred = { viewModel.onWallpaperEvent(WallpaperEvent.SetBlurred(it)) },
                onRemove = { viewModel.onWallpaperEvent(WallpaperEvent.Remove) },
                onDimming = viewModel::setWallpaperDimming,
            )
        }
    ChatAppearanceScreen(
        state = state,
        onTextScale = viewModel::setTextScale,
        onBubbleCorners = viewModel::setBubbleCorners,
        onWallpaper = viewModel::setWallpaper,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        wallpaperActions = wallpaperActions,
    )
}

/**
 * Settings → Appearance → Chat appearance (`REQUIREMENTS.md` FR-S1, `docs/DESIGN_SYSTEM.md` §6 "Chat appearance"): a
 * live preview of a conversation ([ChatPreview]) in the theme's chat style, which follows every change at once; then
 * the message text size and the bubbles' corners as sliders through their steps (a `SegmentTick` per step), and the
 * wallpapers as chips ([WallpaperChip]) in a grid that wraps, one radio group for screen readers.
 */
@Composable
internal fun ChatAppearanceScreen(
    state: ChatAppearanceUiState,
    onTextScale: (Float) -> Unit,
    onBubbleCorners: (BubbleCorners) -> Unit,
    onWallpaper: (ChatWallpaper) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    wallpaperActions: WallpaperActions = WallpaperActions(),
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_chat_appearance),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            ChatAppearanceUiState.Loading -> {
                val loading = stringResource(R.string.settings_appearance_loading)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                }
            }

            is ChatAppearanceUiState.Content -> {
                ChatAppearanceContent(state, onTextScale, onBubbleCorners, onWallpaper, wallpaperActions)
            }
        }
    }
}

@Composable
private fun ChatAppearanceContent(
    state: ChatAppearanceUiState.Content,
    onTextScale: (Float) -> Unit,
    onBubbleCorners: (BubbleCorners) -> Unit,
    onWallpaper: (ChatWallpaper) -> Unit,
    actions: WallpaperActions,
) {
    val appearance = state.appearance
    val picker = state.picker
    // The picker opens on the theme the page shows in.
    val dark = isDarkTheme()
    LaunchedEffect(picker.forDarkTheme == null) { if (picker.forDarkTheme == null) actions.onTheme(dark) }
    val set = picker.forDarkTheme?.let { state.account.forTheme(it) }
    SettingsGroup {
        item { shapes ->
            SettingsSegment(shapes) {
                // Clipped to the segment's corners, which the wallpaper would otherwise square off.
                Box(Modifier.clip(shapes.shape)) {
                    PreviewWallpaper(picker.pending, set, appearance.wallpaperDimming, Modifier.matchParentSize())
                    ChatPreview(drawsWallpaper = false)
                }
            }
        }
    }
    SettingsGroup(title = stringResource(R.string.settings_chat_section_messages)) {
        item { shapes -> TextSizeSegment(appearance.textScale, shapes, onTextScale) }
        item { shapes -> BubbleCornersSegment(appearance.bubbleCorners, shapes, onBubbleCorners) }
    }
    WallpaperGroup(state, set, onWallpaper, actions)
}

/** The theme's containers as 24-bit RGB: the fill other apps draw a pattern picked here on. */
private fun patternFill(colors: ColorScheme): List<Int> =
    listOf(colors.primaryContainer, colors.tertiaryContainer).map { it.toArgb() and RGB }

private const val RGB = 0xFFFFFF

/** Message text size: "Message text size" over the size in percent ("110%"), and a slider through its steps. */
@Composable
private fun TextSizeSegment(
    scale: Float,
    shapes: ListItemShapes,
    onChange: (Float) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val labels =
        remember(locale) {
            val percent = NumberFormat.getPercentInstance(locale)
            TextScaleSteps.map { percent.format(it.toDouble()) }
        }
    StepSlider(
        title = stringResource(R.string.settings_chat_text_size),
        labels = labels,
        index = TextScaleSteps.indexOfFirst { it >= scale - TEXT_SCALE_TOLERANCE }.coerceAtLeast(0),
        shapes = shapes,
        onStep = { onChange(TextScaleSteps[it]) },
    )
}

/** Bubble corners: "Bubble corners" over how round they are ("Very rounded"), and a slider through the shape scale. */
@Composable
private fun BubbleCornersSegment(
    corners: BubbleCorners,
    shapes: ListItemShapes,
    onChange: (BubbleCorners) -> Unit,
) {
    StepSlider(
        title = stringResource(R.string.settings_chat_bubble_corners),
        labels = BubbleCorners.entries.map { it.label() },
        index = corners.ordinal,
        shapes = shapes,
        onStep = { onChange(BubbleCorners.entries[it]) },
    )
}

/**
 * A setting with steps, a row of its group ([shapes]): [title] over the label of step [index] in a list item, and
 * below them a [Slider] through as many steps as [labels], calling [onStep] with each step it reaches, which ticks.
 * Screen readers hear the slider by [title] with the step's label as its state.
 */
@Composable
internal fun StepSlider(
    title: String,
    labels: List<String>,
    index: Int,
    shapes: ListItemShapes,
    onStep: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val value = labels[index]
    // One step per label. The state follows the setting while the finger does not hold it.
    val sliderState =
        remember(labels.size) {
            SliderState(
                index.toFloat(),
                labels.size - 2,
                0f..labels.lastIndex.toFloat(),
            )
        }
    LaunchedEffect(index) { if (!sliderState.isDragging) sliderState.value = index.toFloat() }
    SettingsSegment(shapes) {
        SettingsListItem(shapes = shapes, supportingContent = { Text(value) }, colors = nestedItemColors()) {
            Text(title)
        }
        Slider(
            state = sliderState,
            onValueChange = { position ->
                sliderState.value = position
                val step = position.roundToInt().coerceIn(labels.indices)
                if (step != index) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onStep(step)
                }
            },
            modifier =
                Modifier
                    .padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.small,
                    ).semantics {
                        contentDescription = title
                        stateDescription = value
                    },
        )
    }
}

/** Every step of message text's scale, smallest first. */
private val TextScaleSteps: List<Float> =
    generateSequence(ChatAppearance.MIN_TEXT_SCALE) { it + ChatAppearance.TEXT_SCALE_STEP }
        .takeWhile { it <= ChatAppearance.MAX_TEXT_SCALE + TEXT_SCALE_TOLERANCE }
        .map(ChatAppearance::textScaleOf)
        .toList()

// Steps add up in floating point: a hundredth tells them apart.
private const val TEXT_SCALE_TOLERANCE = 0.01f

@PingogramPreviews
@Composable
private fun ChatAppearanceScreenPreview() {
    PingogramPreview {
        ChatAppearanceScreen(
            state = ChatAppearanceUiState.Content(ChatAppearance()),
            onTextScale = {},
            onBubbleCorners = {},
            onWallpaper = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatAppearanceScreenCustomPreview() {
    val appearance =
        ChatAppearance(
            textScale = 1.2f,
            bubbleCorners = BubbleCorners.Large,
            wallpaper = ChatWallpaper.PrimaryToTertiary,
        )
    PingogramTheme(dynamicColor = false, chat = appearance) {
        ChatAppearanceScreen(
            state = ChatAppearanceUiState.Content(appearance),
            onTextScale = {},
            onBubbleCorners = {},
            onWallpaper = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatAppearanceScreenLoadingPreview() {
    PingogramPreview {
        ChatAppearanceScreen(
            state = ChatAppearanceUiState.Loading,
            onTextScale = {},
            onBubbleCorners = {},
            onWallpaper = {},
            onBack = {},
        )
    }
}
