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
 * The stickers tab: a loading indicator after 200 ms, an empty state without stickers, an error with "Try again"
 * ([EmojiPanelEvent.Retry]), or the sections: the row of sections ([SectionRow]) over a grid of stickers in cells of at
 * least 72 dp. Sets whose sections come within a screen of view load ([EmojiPanelEvent.SetsVisible]), showing tonal
 * placeholders until then.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StickerTabContent(
    tab: StickerTab,
    onSticker: (Sticker) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
) {
    when (tab) {
        StickerTab.Loading -> {
            DelayedLoadingIndicator(Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }

        StickerTab.Empty -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_mood,
                title = stringResource(R.string.panel_stickers_empty_title),
                body = stringResource(R.string.panel_stickers_empty_body),
            )
        }

        is StickerTab.Error -> {
            EmptyState(
                illustration = MaterialShapes.Cookie4Sided,
                icon = UiR.drawable.ic_error,
                title = stringResource(R.string.panel_stickers_error_title),
                body = errorText(tab.error),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Button(onClick = { onEvent(EmojiPanelEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(R.string.panel_stickers_retry),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }

        is StickerTab.Content -> {
            StickerSections(tab.sections, onSticker, onEvent)
        }
    }
}

@Composable
private fun StickerSections(
    sections: ImmutableList<StickerSection>,
    onSticker: (Sticker) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val starts = remember(sections) { sectionStarts(sections.map { it.stickers.size + it.placeholders }) }
    ReportVisibleSets(gridState, sections, starts, onEvent)
    Column {
        SectionRow(
            count = sections.size,
            gridState = gridState,
            starts = starts,
            description = { sections[it].kind.title() },
        ) { index -> SectionIcon(sections[index].kind) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(MaterialTheme.sizes.panelSticker),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        ) {
            sections.forEach { section ->
                item(key = "header:${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                    SectionHeader(section.kind.title())
                }
                items(
                    count = section.stickers.size,
                    key = { "${section.key}:${section.stickers[it].file.id}" },
                    contentType = { STICKER },
                ) { index -> PanelSticker(section.stickers[index], onSticker) }
                items(
                    count = section.placeholders,
                    key = { "${section.key}:placeholder:$it" },
                    contentType = { PLACEHOLDER },
                ) { StickerPlaceholder() }
            }
        }
    }
}

/** A section's icon in [SectionRow]: a set's picture, or the favorite or recent icon. */
@Composable
internal fun SectionIcon(kind: StickerSectionKind) {
    when (kind) {
        StickerSectionKind.Favorites -> PanelIcon(UiR.drawable.ic_favorite)
        StickerSectionKind.Recent -> PanelIcon(UiR.drawable.ic_history)
        is StickerSectionKind.Set -> StickerSetCover(kind.cover)
    }
}

/** A section's title: Favorites, Recently used, or the set's own. */
@Composable
internal fun StickerSectionKind.title(): String =
    when (this) {
        StickerSectionKind.Favorites -> stringResource(R.string.panel_stickers_favorites)
        StickerSectionKind.Recent -> stringResource(R.string.panel_stickers_recent)
        is StickerSectionKind.Set -> title
    }
