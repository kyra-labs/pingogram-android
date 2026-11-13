package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.ui.component.animatePlacement
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.account.StepProgress
import dev.kyralabs.pingogram.feature.settings.ui.account.StepText
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowContainerColor
import kotlinx.collections.immutable.ImmutableList
import java.io.File
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The stickers of the set: "Step 2 of 2 · 5 stickers" and how to change them, then three tiles to a row, each sticker
 * on the rows' container (`surface`) with `extraLarge` corners and its emoji on a badge ([StickerTileItem]), the
 * stickers being made ([PreparingTile]) and "Add sticker" ([AddStickerTile]), each sliding to a new place on the
 * theme's item motion when stickers move, come or go; a footer, why Telegram refused the set in
 * `error`, a [LinearWavyProgressIndicator] while it publishes, and "Publish set" at the end.
 */
@Composable
internal fun ColumnScope.StickersStep(
    state: NewStickerSetUiState,
    onEvent: (NewStickerSetEvent) -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    StepText(
        pluralStringResource(
            R.plurals.settings_new_sticker_set_step_stickers,
            state.stickers.size,
            numbers.format(state.stickers.size),
        ),
    )
    StepText(stringResource(R.string.settings_new_sticker_set_stickers_text))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        maxItemsInEachRow = COLUMNS,
    ) {
        val motion = rememberItemMotion()
        val tile = Modifier.weight(1f).aspectRatio(1f).animatePlacement(motion)
        // Keyed, so a tile that moves keeps its place's state and slides from where it was.
        state.stickers.forEachIndexed { index, sticker ->
            key(sticker.path) { StickerTileItem(index, sticker, state, onEvent, tile) }
        }
        repeat(state.preparing) { index -> key(PREPARING_KEY + index) { PreparingTile(tile) } }
        if (state.canAddSticker) key(ADD_KEY) { AddStickerTile(onEvent, tile) }
        // Empty places keep the last row's tiles as wide as the others.
        val shown = state.stickers.size + state.preparing + if (state.canAddSticker) 1 else 0
        repeat((COLUMNS - shown % COLUMNS) % COLUMNS) { Box(tile) }
    }
    StepText(stringResource(R.string.settings_new_sticker_set_footer))
    state.error?.let { error ->
        Text(
            text = errorText(error),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    StepProgress(state.isPublishing, stringResource(R.string.settings_new_sticker_set_publishing))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { onEvent(NewStickerSetEvent.Publish) },
            enabled = state.canPublish,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(R.string.settings_new_sticker_set_publish),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/**
 * A sticker of the set: its image, fitted, and its emoji on a `surfaceContainerHighest` badge at its bottom end. A tap
 * chooses its emoji; a long press (the `LongPress` haptic) opens an expressive menu of "Move earlier", "Move later" and
 * "Remove" (in `error`). Screen readers hear "Sticker 3, emoji 😂" and get the menu's items as custom actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTileItem(
    index: Int,
    sticker: StickerTile,
    state: NewStickerSetUiState,
    onEvent: (NewStickerSetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val description = stringResource(R.string.settings_new_sticker_set_sticker, index + 1, sticker.emoji)
    val actions = tileActions(index, state.stickers.size, onEvent)
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(settingsRowContainerColor())
                    .combinedClickable(
                        enabled = !state.isPublishing,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEvent(NewStickerSetEvent.OpenMenu(index))
                        },
                        onClick = { onEvent(NewStickerSetEvent.ChooseEmoji(index)) },
                    ).semantics {
                        contentDescription = description
                        role = Role.Button
                        customActions =
                            actions.map { action ->
                                CustomAccessibilityAction(action.label) {
                                    action.run()
                                    true
                                }
                            }
                    },
        ) {
            StickerImage(sticker.path, Modifier.fillMaxSize().padding(MaterialTheme.spacing.small))
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MaterialTheme.spacing.small)
                        .size(MaterialTheme.sizes.avatarExtraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { Text(sticker.emoji, style = MaterialTheme.typography.titleMedium) }
        }
        if (state.menuFor == index) TileMenu(actions, onDismiss = { onEvent(NewStickerSetEvent.CloseMenu) })
    }
}

/** An action of a tile's menu: its [label], whether it [isDestructive], and what it does. */
private class TileAction(
    val label: String,
    val isDestructive: Boolean,
    val run: () -> Unit,
)

/** Moving the sticker earlier or later where it can, and removing it. */
@Composable
private fun tileActions(
    index: Int,
    count: Int,
    onEvent: (NewStickerSetEvent) -> Unit,
): List<TileAction> {
    val earlier = stringResource(R.string.settings_new_sticker_set_move_earlier)
    val later = stringResource(R.string.settings_new_sticker_set_move_later)
    val remove = stringResource(R.string.settings_new_sticker_set_remove)
    return listOfNotNull(
        TileAction(earlier, false) { onEvent(NewStickerSetEvent.Move(index, -1)) }.takeIf { index > 0 },
        TileAction(later, false) { onEvent(NewStickerSetEvent.Move(index, 1)) }.takeIf { index < count - 1 },
        TileAction(remove, true) { onEvent(NewStickerSetEvent.Remove(index)) },
    )
}

/** A tile's expressive menu: moving in one group, removing in `error` in a second. */
@Composable
private fun TileMenu(
    actions: List<TileAction>,
    onDismiss: () -> Unit,
) {
    val groups = actions.partition { !it.isDestructive }.toList().filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = true, onDismissRequest = onDismiss) {
        groups.forEachIndexed { groupIndex, group ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)) {
                group.forEachIndexed { index, action ->
                    DropdownMenuItem(
                        onClick = action.run,
                        text = { Text(action.label) },
                        shape = MenuDefaults.itemShape(index, group.size).shape,
                        colors =
                            if (action.isDestructive) {
                                MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                            } else {
                                MenuDefaults.itemColors()
                            },
                    )
                }
            }
        }
    }
}

/** A sticker being made from a picked photo: a [LoadingIndicator] on the rows' container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreparingTile(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.settings_new_sticker_set_preparing)
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(settingsRowContainerColor())
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { LoadingIndicator() }
}

/** "Add sticker": the add symbol over its label in an `outline` border with `extraLarge` corners; one button. */
@Composable
private fun AddStickerTile(
    onEvent: (NewStickerSetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.settings_new_sticker_set_add)
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .border(
                    DividerDefaults.Thickness * 2,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.extraLarge,
                ).combinedClickable(onClick = { onEvent(NewStickerSetEvent.AddSticker) })
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label) {
                        onEvent(NewStickerSetEvent.AddSticker)
                        true
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterVertically),
    ) {
        Icon(painter = painterResource(UiR.drawable.ic_add), contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelMediumEmphasized)
    }
}

/** The set's first stickers in a row on a pill of the rows' container; decorative. */
@Composable
internal fun StickerStrip(stickers: ImmutableList<StickerTile>) {
    Row(
        modifier =
            Modifier
                .padding(top = MaterialTheme.spacing.small)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(settingsRowContainerColor())
                .padding(MaterialTheme.spacing.medium)
                .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        stickers.take(STRIP_SIZE).forEach { StickerImage(it.path, Modifier.size(MaterialTheme.sizes.avatarMedium)) }
    }
}

/** A sticker's image from its copy on the device, fitted; decorative. */
@Composable
private fun StickerImage(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = MaterialTheme.sizes.sharedMediaCell,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val request =
        remember(context, path, sizePx) {
            ImageRequest
                .Builder(context)
                .data(File(path))
                .size(sizePx)
                .build()
        }
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

private const val COLUMNS = 3
private const val PREPARING_KEY = "preparing:"
private const val ADD_KEY = "add"
private const val STRIP_SIZE = 5
