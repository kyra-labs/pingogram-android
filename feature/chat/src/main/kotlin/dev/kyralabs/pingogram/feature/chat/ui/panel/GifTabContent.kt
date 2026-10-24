package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.MinithumbnailPlaceholder
import dev.kyralabs.pingogram.feature.chat.ui.content.TdlibImage
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.feature.chat.ui.content.fadingOutAs
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberImageAlpha
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The GIF tab: the search bar's input field ("Search GIFs") where Telegram names a bot that searches them, over the
 * saved GIFs or, while the field holds a query, what the search found. GIFs show as square cells of at least
 * `sizes.sharedMediaCell`, their still picture over the blurred minithumbnail, dimmed in dark theme as the
 * conversation's media, with a "GIF" label; a tap sends one ([onGif], a confirming haptic), a long press on a saved one
 * opens its menu ("Remove"). The search's next page loads near the end of its results. Whether the search field has
 * the focus goes to [onSearchFocus].
 */
@Composable
internal fun GifTabContent(
    tab: GifTab,
    onGif: (Animation) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
    onSearchFocus: (Boolean) -> Unit = {},
) {
    when (tab) {
        GifTab.Loading -> {
            DelayedLoadingIndicator(Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }

        is GifTab.Error -> {
            GifsFailed(errorText(tab.error), onRetry = { onEvent(EmojiPanelEvent.RetryGifs) })
        }

        is GifTab.Content -> {
            Column(Modifier.fillMaxSize()) {
                if (tab.canSearch) {
                    GifSearchField(tab.search?.query.orEmpty(), onSearchFocus) {
                        onEvent(EmojiPanelEvent.GifQueryChanged(it))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val search = tab.search
                    if (search == null) {
                        SavedGifs(tab.saved, onGif, onEvent)
                    } else {
                        FoundGifs(search, onGif, onEvent)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedGifs(
    saved: ImmutableList<Animation>,
    onGif: (Animation) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
) {
    if (saved.isEmpty()) {
        EmptyState(
            illustration = MaterialShapes.Cookie9Sided,
            icon = UiR.drawable.ic_image,
            title = stringResource(R.string.panel_gifs_empty_title),
            body = stringResource(R.string.panel_gifs_empty_body),
        )
    } else {
        GifGrid(saved, onGif, onRemove = { onEvent(EmojiPanelEvent.RemoveSavedGif(it)) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FoundGifs(
    search: GifSearch,
    onGif: (Animation) -> Unit,
    onEvent: (EmojiPanelEvent) -> Unit,
) {
    when {
        search.results.isEmpty() && search.isLoading -> {
            DelayedLoadingIndicator(Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }

        search.results.isEmpty() && search.error != null -> {
            GifsFailed(errorText(search.error), onRetry = { onEvent(EmojiPanelEvent.RetryGifs) })
        }

        search.results.isEmpty() -> {
            Text(
                text = stringResource(R.string.panel_gifs_no_results),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .wrapContentSize(
                            Alignment.Center,
                        ).padding(MaterialTheme.spacing.large),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        else -> {
            val gridState = rememberLazyGridState()
            LoadMoreNearEnd(gridState, search) { onEvent(EmojiPanelEvent.MoreGifs) }
            GifGrid(search.results, onGif, onRemove = null, gridState = gridState) {
                when {
                    search.isLoading -> {
                        LoadingIndicator(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center))
                    }

                    search.error != null -> {
                        TextButton(
                            onClick = { onEvent(EmojiPanelEvent.RetryGifs) },
                            modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.Center),
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.panel_gifs_retry)) }
                    }
                }
            }
        }
    }
}

/** Asks for the next page ([onLoadMore]) once the grid shows one of the last [LOAD_MORE_WITHIN] results. */
@Composable
private fun LoadMoreNearEnd(
    gridState: LazyGridState,
    search: GifSearch,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val count = search.results.size
    LaunchedEffect(gridState, count, search.hasMore) {
        if (!search.hasMore) return@LaunchedEffect
        snapshotFlow {
            (
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            ) >= count - LOAD_MORE_WITHIN
        }.distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/**
 * [gifs] in square cells, as many across as `sizes.sharedMediaCell` allows, `mediaAlbumGap` apart; [footer] below them
 * across the grid. With [onRemove], a long press opens a cell's menu.
 */
@Composable
private fun GifGrid(
    gifs: ImmutableList<Animation>,
    onGif: (Animation) -> Unit,
    onRemove: ((Animation) -> Unit)?,
    gridState: LazyGridState = rememberLazyGridState(),
    footer: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = MaterialTheme.sizes.mediaAlbumGap
        val padding = MaterialTheme.spacing.small
        val width = maxWidth - padding * 2
        val columns = (width / MaterialTheme.sizes.sharedMediaCell).toInt().coerceAtLeast(1)
        val cell = (width - gap * (columns - 1)) / columns
        val cellPx = with(LocalDensity.current) { cell.roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = padding, vertical = MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(count = gifs.size, key = { gifs[it].file.id }, contentType = { GIF }) { index ->
                GifCell(gifs[index], cell, cellPx, onGif, onRemove)
            }
            if (footer != null) {
                item(key = FOOTER, span = { GridItemSpan(maxLineSpan) }, contentType = FOOTER) { footer() }
            }
        }
    }
}

/**
 * A GIF as a square cell of [cellSize], `extraSmall` corners: its still picture (its thumbnail, when that is a
 * picture) decoded at [cellPx] over the blurred minithumbnail, and a "GIF" label. Screen readers hear "GIF", send it
 * with the click and remove it with a custom action where [onRemove] is given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GifCell(
    gif: Animation,
    cellSize: Dp,
    cellPx: Int,
    onGif: (Animation) -> Unit,
    onRemove: ((Animation) -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(UiR.string.preview_gif)
    val send = stringResource(R.string.panel_gifs_send)
    val remove = stringResource(R.string.panel_gifs_remove)
    val colorFilter = MaterialTheme.pingogramColors.mediaColorFilter
    Box {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraSmall)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = send,
                    onLongClick = onRemove?.let { { menuOpen = true } },
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onGif(gif)
                }.semantics {
                    contentDescription = description
                    if (onRemove != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(remove) {
                                    onRemove(gif)
                                    true
                                },
                            )
                    }
                },
        ) {
            val picture = gif.thumbnail?.asPicture()?.file
            val alpha = rememberImageAlpha(picture?.id ?: 0)
            MinithumbnailPlaceholder(
                gif.minithumbnail,
                DpSize(cellSize, cellSize),
                Modifier.matchParentSize().fadingOutAs(alpha),
                colorFilter,
            )
            if (picture != null) {
                TdlibImage(picture.id, cellPx, cellPx, alpha, Modifier.matchParentSize(), colorFilter = colorFilter)
            }
            MediaLabel(description, Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.extraSmall))
        }
        if (onRemove != null) {
            RemoveMenu(menuOpen, onDismiss = { menuOpen = false }) { onRemove(gif) }
        }
    }
}

/** A saved GIF's menu: "Remove" in `error`. */
@Composable
private fun RemoveMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenuPopup(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            DropdownMenuItem(
                onClick = {
                    onDismiss()
                    onRemove()
                },
                text = { Text(stringResource(R.string.panel_gifs_remove)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** Why GIFs could not be loaded ([message]): a `Cookie4Sided` badge on `errorContainer` with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GifsFailed(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.panel_gifs_error_title),
        body = message,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.panel_gifs_retry), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/**
 * The search bar's input field on its container ("Search GIFs"), starting with [initial] (the search the tab shows,
 * when it comes back); each change goes to [onQueryChange], whether it has the focus to [onFocus] (false again when it
 * leaves the screen).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GifSearchField(
    initial: String,
    onFocus: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(initial)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnFocus by rememberUpdatedState(onFocus)
    DisposableEffect(Unit) { onDispose { currentOnFocus(false) } }
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth().onFocusChanged { currentOnFocus(it.hasFocus) },
            placeholder = { Text(stringResource(R.string.panel_gifs_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.panel_gifs_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

/** Results from the end at which the next page of a search loads. */
private const val LOAD_MORE_WITHIN = 6

private const val GIF = "gif"
private const val FOOTER = "footer"
