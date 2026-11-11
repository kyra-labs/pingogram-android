package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.collections.immutable.ImmutableList

/**
 * "Add text": a `BasicAlertDialog` in the alert dialog's shape and colors, "Add text" in `titleLargeEmphasized` (a
 * heading) over a filled one-line `TextField` (user content, at most [StickerMakerLimits.TEXT_LENGTH_MAX]
 * characters, focused as it opens, what is typed kept across process death), then "Cancel" and "Add", disabled while
 * blank. The keyboard's Done adds too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerTextDialog(onEvent: (MakeStickerEvent) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val add = { onEvent(MakeStickerEvent.TextEntered(text)) }
    BasicAlertDialog(onDismissRequest = { onEvent(MakeStickerEvent.TextEntered(null)) }) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.make_sticker_add_text),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                TextField(
                    value = text,
                    onValueChange = { text = it.take(StickerMakerLimits.TEXT_LENGTH_MAX) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge.userContent,
                    label = { Text(stringResource(R.string.make_sticker_text_label)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) add() }),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
                ) {
                    TextButton(
                        onClick = { onEvent(MakeStickerEvent.TextEntered(null)) },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.make_sticker_cancel)) }
                    Button(onClick = add, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                        Text(
                            stringResource(R.string.make_sticker_add),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(focus) { focus.requestFocus() }
}

/**
 * The emoji to draw on the sticker or to suggest it for ([target]): a `ModalBottomSheet` that opens expanded, its
 * title in `titleLargeEmphasized` (a heading), then Unicode's emoji under their groups' names (`titleSmallEmphasized`
 * in `onSurfaceVariant`, headings) in 48 dp cells (`sizes.panelEmoji`), as the composer's panel shows them; a delayed
 * indicator until they are read. A tap chooses one and closes the sheet; the scrim, dragging and back close it
 * without.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerEmojiSheet(
    target: EmojiTarget,
    groups: ImmutableList<EmojiGroup>,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(MakeStickerEvent.EmojiChosen(null)) },
        sheetState = sheetState,
    ) {
        StickerEmojiChoices(target, groups, onEvent)
    }
}

/** The emoji sheet's content: [StickerEmojiSheet]. */
@Composable
internal fun StickerEmojiChoices(
    target: EmojiTarget,
    groups: ImmutableList<EmojiGroup>,
    onEvent: (MakeStickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val loading = stringResource(R.string.make_sticker_emoji_loading)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.panelEmoji),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
    ) {
        item(key = TITLE, span = { GridItemSpan(maxLineSpan) }, contentType = TITLE) {
            Text(
                text =
                    stringResource(
                        if (target ==
                            EmojiTarget.Layer
                        ) {
                            R.string.make_sticker_add_emoji
                        } else {
                            R.string.make_sticker_emoji_title
                        },
                    ),
                modifier = Modifier.padding(bottom = spacing.medium).semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        if (groups.isEmpty()) {
            item(key = LOADING, span = { GridItemSpan(maxLineSpan) }, contentType = LOADING) {
                Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.inputPanelHeight), Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                }
            }
        }
        groups.forEach { group ->
            item(key = group.category, span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                Text(
                    text = stringResource(group.category.label),
                    modifier = Modifier.padding(vertical = spacing.small).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            items(group.emoji, key = { "${group.category}:$it" }, contentType = { EMOJI }) { emoji ->
                Box(
                    modifier =
                        Modifier
                            .size(MaterialTheme.sizes.panelEmoji)
                            .clickable { onEvent(MakeStickerEvent.EmojiChosen(emoji)) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
            }
        }
    }
}

@get:StringRes
private val EmojiCategory.label: Int
    get() =
        when (this) {
            EmojiCategory.SmileysAndEmotion -> R.string.make_sticker_emoji_smileys
            EmojiCategory.PeopleAndBody -> R.string.make_sticker_emoji_people
            EmojiCategory.AnimalsAndNature -> R.string.make_sticker_emoji_animals
            EmojiCategory.FoodAndDrink -> R.string.make_sticker_emoji_food
            EmojiCategory.TravelAndPlaces -> R.string.make_sticker_emoji_travel
            EmojiCategory.Activities -> R.string.make_sticker_emoji_activities
            EmojiCategory.Objects -> R.string.make_sticker_emoji_objects
            EmojiCategory.Symbols -> R.string.make_sticker_emoji_symbols
            EmojiCategory.Flags -> R.string.make_sticker_emoji_flags
        }

private const val TITLE = "title"
private const val LOADING = "loading"
private const val HEADER = "header"
private const val EMOJI = "emoji"
