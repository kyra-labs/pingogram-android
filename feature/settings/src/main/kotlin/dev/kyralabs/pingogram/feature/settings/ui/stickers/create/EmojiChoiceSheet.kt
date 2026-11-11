package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList

/**
 * The emoji of sticker [index], a `ModalBottomSheet` that opens expanded: "Emoji for sticker 3" in
 * `titleLargeEmphasized` (a heading), then [EmojiChoices]. A tap chooses one and closes the sheet; the scrim, dragging
 * and back close it keeping the emoji the sticker has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiChoiceSheet(
    index: Int,
    groups: ImmutableList<EmojiGroup>,
    onEvent: (NewStickerSetEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(NewStickerSetEvent.CloseEmoji) }, sheetState = sheetState) {
        EmojiChoices(index, groups, onEvent)
    }
}

/**
 * The sheet's content: its title, then Unicode's emoji under their groups' names (`titleSmallEmphasized` in
 * `onSurfaceVariant`, headings) in a grid of 48 dp cells (`sizes.panelEmoji`), as the composer's emoji panel shows
 * them; a delayed indicator until the emoji are read.
 */
@Composable
internal fun EmojiChoices(
    index: Int,
    groups: ImmutableList<EmojiGroup>,
    onEvent: (NewStickerSetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val loading = stringResource(R.string.settings_new_sticker_set_emoji_loading)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MaterialTheme.sizes.panelEmoji),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
    ) {
        item(key = TITLE, span = { GridItemSpan(maxLineSpan) }, contentType = TITLE) {
            Text(
                text = stringResource(R.string.settings_new_sticker_set_emoji_title, index + 1),
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
                            .clickable { onEvent(NewStickerSetEvent.EmojiChosen(emoji)) },
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
            EmojiCategory.SmileysAndEmotion -> R.string.settings_new_sticker_set_emoji_smileys
            EmojiCategory.PeopleAndBody -> R.string.settings_new_sticker_set_emoji_people
            EmojiCategory.AnimalsAndNature -> R.string.settings_new_sticker_set_emoji_animals
            EmojiCategory.FoodAndDrink -> R.string.settings_new_sticker_set_emoji_food
            EmojiCategory.TravelAndPlaces -> R.string.settings_new_sticker_set_emoji_travel
            EmojiCategory.Activities -> R.string.settings_new_sticker_set_emoji_activities
            EmojiCategory.Objects -> R.string.settings_new_sticker_set_emoji_objects
            EmojiCategory.Symbols -> R.string.settings_new_sticker_set_emoji_symbols
            EmojiCategory.Flags -> R.string.settings_new_sticker_set_emoji_flags
        }

private const val TITLE = "title"
private const val LOADING = "loading"
private const val HEADER = "header"
private const val EMOJI = "emoji"
