package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Making a sticker from a photo (board 14a): a small `TopAppBar` with close and "Make a sticker" in
 * `titleLargeEmphasized`, "Next" a default `Button` at its end; then, at most `contentMaxWidth` wide inside the window
 * margin, the sticker's square with `extraLarge` corners over a checkered ground that stands for transparency
 * ([StickerCanvas]), the tools ([StickerTools]) and what the user can do with the photo or the layer they touched. The
 * text dialog, the emoji sheet and the sheet that adds the sticker to a set ([AddToSetSheet]) show over it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MakeStickerScreen(
    state: MakeStickerUiState,
    photo: Painter,
    capture: GraphicsLayer?,
    onEvent: (MakeStickerEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { MakeStickerTopBar(state, onEvent, onClose) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = windowMargin())
                        .padding(bottom = MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                EditorArea(state, photo, capture, onEvent)
                StickerTools(state, onEvent)
                EditorHint(state, onEvent)
            }
        }
    }
    StickerOverlays(state, onEvent)
}

/** Close at the start, the title, and "Next" (a `LoadingIndicator` in its place while the sticker is written). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MakeStickerTopBar(
    state: MakeStickerUiState,
    onEvent: (MakeStickerEvent) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.make_sticker_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(UiR.drawable.ic_close),
                    contentDescription = stringResource(R.string.make_sticker_close),
                )
            }
        },
        actions = {
            val writing = stringResource(R.string.make_sticker_writing)
            Button(
                onClick = { onEvent(MakeStickerEvent.Next) },
                modifier = Modifier.padding(end = MaterialTheme.spacing.small),
                enabled = state.canFinish,
                shapes = ButtonDefaults.shapes(),
            ) {
                if (state.rendering) {
                    LoadingIndicator(
                        modifier =
                            Modifier
                                .size(MaterialTheme.sizes.inlineProgressIndicator)
                                .semantics { contentDescription = writing },
                    )
                } else {
                    Text(
                        stringResource(R.string.make_sticker_next),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        },
    )
}

/**
 * The sticker's square: [StickerCanvas] once the photo is on the device; until then its download's progress (a
 * determinate `CircularWavyProgressIndicator` once TDLib knows the size, a delayed indicator before), or why it
 * failed with "Try again".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorArea(
    state: MakeStickerUiState,
    photo: Painter,
    capture: GraphicsLayer?,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    val light = MaterialTheme.colorScheme.surfaceContainerLow
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    val cell = with(LocalDensity.current) { MaterialTheme.spacing.large.toPx() }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .drawBehind { drawChecks(light, dark, cell) },
        contentAlignment = Alignment.Center,
    ) {
        when (val shown = state.photo) {
            is StickerPhoto.Ready -> {
                StickerCanvas(state, photo, capture, onEvent, Modifier.fillMaxSize())
            }

            is StickerPhoto.Loading -> {
                val loading = stringResource(R.string.make_sticker_loading)
                val description = Modifier.semantics { contentDescription = loading }
                if (shown.progress > 0f) {
                    CircularWavyProgressIndicator(progress = { shown.progress }, modifier = description)
                } else {
                    DelayedLoadingIndicator(description)
                }
            }

            StickerPhoto.Failed -> {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.make_sticker_photo_failed),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onEvent(MakeStickerEvent.RetryPhoto) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.make_sticker_retry))
                    }
                }
            }
        }
    }
}

/** A checkered ground of [cell]-wide squares in [light] and [dark], which stands for transparency. */
private fun DrawScope.drawChecks(
    light: Color,
    dark: Color,
    cell: Float,
) {
    drawRect(light)
    val columns = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 1) {
                drawRect(
                    color = dark,
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

/**
 * "Add text", "Add emoji" and "Outline" as a connected group sharing the width (`ButtonGroupDefaults` connected shapes
 * with their press morph, `ConnectedSpaceBetween` apart): the first two `FilledTonalButton`s, disabled once the
 * sticker holds [StickerMakerLimits.LAYER_COUNT_MAX] layers or while its photo is not there; "Outline" a
 * `ToggleButton`, checked while on. From 150% font the labels take the icons' room, and two lines where they need it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StickerTools(
    state: MakeStickerUiState,
    onEvent: (MakeStickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdd = state.photo is StickerPhoto.Ready && state.layers.size < StickerMakerLimits.LAYER_COUNT_MAX
    val showsIcon = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    Row(
        // One height for the three, the tallest label's.
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
        FilledTonalButton(
            onClick = { onEvent(MakeStickerEvent.AddText) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = canAdd,
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        ) {
            if (showsIcon) {
                Text(
                    stringResource(R.string.make_sticker_text_glyph),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            ToolLabel(R.string.make_sticker_add_text, showsIcon)
        }
        val middle = ButtonGroupDefaults.connectedMiddleButtonShapes()
        FilledTonalButton(
            onClick = { onEvent(MakeStickerEvent.AddEmoji) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = canAdd,
            shapes = ButtonShapes(middle.shape, middle.pressedShape),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        ) {
            ToolIcon(UiR.drawable.ic_mood, showsIcon)
            ToolLabel(R.string.make_sticker_add_emoji, showsIcon)
        }
        ToggleButton(
            checked = state.outline,
            onCheckedChange = { onEvent(MakeStickerEvent.ToggleOutline) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = state.photo is StickerPhoto.Ready,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        ) {
            ToolIcon(UiR.drawable.ic_edit, showsIcon)
            ToolLabel(R.string.make_sticker_outline, showsIcon)
        }
    }
}

@Composable
private fun ToolIcon(
    @DrawableRes icon: Int,
    shows: Boolean,
) {
    if (shows) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
}

@Composable
private fun ToolLabel(
    label: Int,
    afterIcon: Boolean,
) {
    Text(
        text = stringResource(label),
        modifier = if (afterIcon) Modifier.padding(start = ButtonDefaults.IconSpacing) else Modifier,
        // Large fonts take a second line rather than cutting the label off; the button grows with it.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Under the tools: how to crop the photo and place what is on it, in `bodyMedium` `onSurfaceVariant`, centred; while
 * a layer is touched, that it drags, and "Remove" (an `error` `TextButton` naming it) to take it off.
 */
@Composable
private fun EditorHint(
    state: MakeStickerUiState,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    val selected = state.layers.firstOrNull { it.id == state.selected }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (selected ==
                        null
                    ) {
                        R.string.make_sticker_hint
                    } else {
                        R.string.make_sticker_hint_layer
                    },
                ),
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (selected != null) {
            val description =
                stringResource(
                    if (selected.kind ==
                        LayerKind.Text
                    ) {
                        R.string.make_sticker_remove_text
                    } else {
                        R.string.make_sticker_remove_emoji
                    },
                    selected.content,
                )
            TextButton(
                onClick = { onEvent(MakeStickerEvent.Remove(selected.id)) },
                modifier = Modifier.semantics { contentDescription = description },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.make_sticker_remove)) }
        }
    }
}

/** The text dialog, the emoji sheet and the sheet that adds the sticker to a set, while each shows. */
@Composable
private fun StickerOverlays(
    state: MakeStickerUiState,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    if (state.textDialog) StickerTextDialog(onEvent)
    state.emojiFor?.let { target -> StickerEmojiSheet(target, state.emojiGroups, onEvent) }
    state.sheet?.let { sheet -> AddToSetSheet(sheet, onEvent) }
}

/** From this font scale on, the tools show their labels without icons. */
private const val LARGE_FONT_SCALE = 1.5f
