package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The emoji and sticker panel (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6), in the keyboard's place below the
 * composer, on the composer's `surfaceContainer`: the open tab's content, and below it the tabs (Emoji, Stickers) as a
 * connected [ButtonGroupDefaults] pair of [ToggleButton]s, with the backspace key at the end on the emoji tab. The tab
 * stays across rotations ([initialTab] first); without stickers ([showStickers] false, while editing a message) only
 * emoji show.
 *
 * - **Emoji** ([EmojiTabContent]): a row of category buttons over a grid in sections, recent emoji first. Tapping one
 *   puts it into the text ([onEmoji]) with a keyboard tap haptic.
 * - **Stickers** ([StickerTabContent]): a row of sections (favorites, recent, each set by its picture) over a grid of
 *   stickers; tapping one sends it ([onSticker]).
 */
@Suppress("LongParameterList") // What it shows, and where emoji, the backspace, stickers and loads go.
@Composable
internal fun EmojiPanel(
    state: EmojiPanelUiState,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onSticker: (Sticker) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
    modifier: Modifier = Modifier,
    showStickers: Boolean = true,
    initialTab: PanelTab = PanelTab.Emoji,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val shownTab = if (showStickers) tab else PanelTab.Emoji
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (shownTab) {
                PanelTab.Emoji -> EmojiTabContent(state.emoji, onEmoji)
                PanelTab.Stickers -> StickerTabContent(state.stickers, onSticker, onEvent)
            }
        }
        PanelTabBar(shownTab, onSelect = { tab = it }, showStickers = showStickers, onBackspace = onBackspace)
    }
}

/** The panel's tabs. */
internal enum class PanelTab {
    Emoji,
    Stickers,
}

/**
 * The tabs as a connected button group in the middle of a 56 dp row (the buttons' 48 dp touch targets), the selected
 * label `labelLargeEmphasized`; on the emoji tab, the backspace key at the end, which deletes on each tap.
 */
@Composable
private fun PanelTabBar(
    tab: PanelTab,
    onSelect: (PanelTab) -> Unit,
    showStickers: Boolean,
    onBackspace: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
    ) {
        if (showStickers) {
            val tabs = PanelTab.entries
            val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            Row(
                modifier = Modifier.align(Alignment.Center).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                tabs.forEachIndexed { index, entry ->
                    val selected = entry == tab
                    val label = stringResource(entry.label)
                    Box(Modifier.panelTab(label, selected, onSelect = { onSelect(entry) })) {
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = { onSelect(entry) },
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                            colors = colors,
                        ) {
                            val typography = MaterialTheme.typography
                            Text(
                                text = label,
                                maxLines = 1,
                                style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
        if (tab == PanelTab.Emoji) {
            IconButton(
                onClick = onBackspace,
                modifier = Modifier.align(Alignment.CenterEnd),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(UiR.drawable.ic_backspace), stringResource(R.string.panel_backspace))
            }
        }
    }
}

private val PanelTab.label: Int
    get() =
        when (this) {
            PanelTab.Emoji -> R.string.panel_tab_emoji
            PanelTab.Stickers -> R.string.panel_tab_stickers
        }

/**
 * The emoji tab: the category buttons ([SectionRow]) above the grid, which follows it: tapping a category scrolls to
 * its section, and scrolling selects the category of the topmost row. Sections have a `titleSmallEmphasized` header in
 * `onSurfaceVariant`. Emoji are drawn about 28 dp tall whatever the font scale, as pictures, in cells of at least
 * 48 dp.
 */
@Composable
private fun EmojiTabContent(
    tab: EmojiTab,
    onEmoji: (String) -> Unit,
) {
    when (tab) {
        EmojiTab.Loading -> {
            DelayedLoadingIndicator(Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }

        is EmojiTab.Content -> {
            val sections = tab.sections
            val gridState = rememberLazyGridState()
            val starts = remember(sections) { sectionStarts(sections.map { it.emoji.size }) }
            val haptics = LocalHapticFeedback.current
            val emojiStyle = emojiTextStyle(MaterialTheme.sizes.panelEmoji)
            Column {
                SectionRow(
                    count = sections.size,
                    gridState = gridState,
                    starts = starts,
                    description = { stringResource(sections[it].category.label) },
                ) { index -> PanelIcon(sections[index].category.icon) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(MaterialTheme.sizes.panelEmoji),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
                ) {
                    sections.forEach { section ->
                        val key = section.category?.name ?: RECENT_KEY
                        item(key = "header:$key", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                            SectionHeader(stringResource(section.category.label))
                        }
                        items(
                            count = section.emoji.size,
                            key = { "$key:${section.emoji[it]}" },
                            contentType = { EMOJI },
                        ) { index ->
                            val emoji = section.emoji[index]
                            EmojiCell(emoji, emojiStyle) {
                                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                onEmoji(emoji)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One emoji, which [onClick] puts into the text; screen readers read the emoji's name, as a button. */
@Composable
private fun EmojiCell(
    emoji: String,
    style: TextStyle,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = emoji, style = style, maxLines = 1, softWrap = false)
    }
}

/** The title of an emoji section; null is the recent emoji. */
private val EmojiCategory?.label: Int
    get() =
        when (this) {
            null -> R.string.panel_emoji_recent
            EmojiCategory.SmileysAndEmotion -> R.string.panel_emoji_smileys
            EmojiCategory.PeopleAndBody -> R.string.panel_emoji_people
            EmojiCategory.AnimalsAndNature -> R.string.panel_emoji_animals
            EmojiCategory.FoodAndDrink -> R.string.panel_emoji_food
            EmojiCategory.TravelAndPlaces -> R.string.panel_emoji_travel
            EmojiCategory.Activities -> R.string.panel_emoji_activities
            EmojiCategory.Objects -> R.string.panel_emoji_objects
            EmojiCategory.Symbols -> R.string.panel_emoji_symbols
            EmojiCategory.Flags -> R.string.panel_emoji_flags
        }

/** The Material Symbol of an emoji section; null is the recent emoji. */
private val EmojiCategory?.icon: Int
    get() =
        when (this) {
            null -> UiR.drawable.ic_history
            EmojiCategory.SmileysAndEmotion -> UiR.drawable.ic_mood
            EmojiCategory.PeopleAndBody -> UiR.drawable.ic_emoji_people
            EmojiCategory.AnimalsAndNature -> UiR.drawable.ic_emoji_nature
            EmojiCategory.FoodAndDrink -> UiR.drawable.ic_emoji_food_beverage
            EmojiCategory.TravelAndPlaces -> UiR.drawable.ic_emoji_transportation
            EmojiCategory.Activities -> UiR.drawable.ic_sports_soccer
            EmojiCategory.Objects -> UiR.drawable.ic_emoji_objects
            EmojiCategory.Symbols -> UiR.drawable.ic_emoji_symbols
            EmojiCategory.Flags -> UiR.drawable.ic_flag
        }

/**
 * The text style of an emoji in a cell of [cell]: a size in pixels that follows the cell, not the font scale (an emoji
 * is a picture there, and a larger one would not fit its cell), centred on one line.
 */
@Composable
private fun emojiTextStyle(cell: Dp): TextStyle {
    val style = MaterialTheme.typography.headlineMedium
    val density = LocalDensity.current
    return remember(style, density, cell) {
        val fontSize = with(density) { (cell * EMOJI_SIZE_RATIO).toSp() }
        style.copy(fontSize = fontSize, lineHeight = fontSize, textAlign = TextAlign.Center)
    }
}

// An emoji glyph is about 1.2 times as tall as its font size: at this ratio it fills about 70% of its cell.
private const val EMOJI_SIZE_RATIO = 0.58f

private const val RECENT_KEY = "recent"
private const val EMOJI = "emoji"

/** Content types of the panel's grids. */
internal const val HEADER = "header"
internal const val STICKER = "sticker"
internal const val PLACEHOLDER = "placeholder"

@PingogramPreviews
@Composable
private fun EmojiPanelEmojiPreview() {
    PingogramPreview {
        EmojiPanel(PanelPreviewData.content, {}, {}, {}, {}, Modifier.fillMaxWidth().aspectRatio(PREVIEW_RATIO))
    }
}

@PingogramPreviews
@Composable
private fun EmojiPanelLoadingPreview() {
    PingogramPreview {
        EmojiPanel(EmojiPanelUiState(), {}, {}, {}, {}, Modifier.fillMaxWidth().aspectRatio(PREVIEW_RATIO))
    }
}

@PingogramPreviews
@Composable
private fun EmojiPanelStickersPreview() {
    PingogramPreview {
        EmojiPanel(
            PanelPreviewData.content,
            {},
            {},
            {},
            {},
            Modifier.fillMaxWidth().aspectRatio(PREVIEW_RATIO),
            initialTab = PanelTab.Stickers,
        )
    }
}

@PingogramPreviews
@Composable
private fun EmojiPanelStickersErrorPreview() {
    PingogramPreview {
        EmojiPanel(
            PanelPreviewData.stickersError,
            {},
            {},
            {},
            {},
            Modifier.fillMaxWidth().aspectRatio(PREVIEW_RATIO),
            initialTab = PanelTab.Stickers,
        )
    }
}

private const val PREVIEW_RATIO = 1.3f
