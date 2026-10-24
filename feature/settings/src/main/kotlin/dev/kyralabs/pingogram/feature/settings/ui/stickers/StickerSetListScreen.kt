package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.sticker.StickerCover
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickersPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Trending stickers ([archived] false) or Archived stickers: hoists [StickerSetListViewModel] and shows what it did
 * and its failures in snackbars; a tapped set opens its sheet ([onOpenSet]); [onBack] leaves the page.
 */
@Composable
internal fun StickerSetListRoute(
    archived: Boolean,
    onOpenSet: (setId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StickerSetListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is StickerSetListEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }

                    is StickerSetListEffect.Added -> {
                        resources.getString(R.string.settings_stickers_added_done, effect.title.bidiIsolated())
                    }
                }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    StickerSetListScreen(
        archived = archived,
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        onOpenSet = onOpenSet,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The sets trending on Telegram, or those the user archived ([archived]), in Telegram's order: each with its cover,
 * its title (user content) over how many stickers it has, and "Add" at the end (a `LoadingIndicator` while it is
 * added, a check once the user has it); a footer says what the page is for.
 */
@Suppress("LongParameterList") // A page's kind, state and events, its way out, and its hoisted states.
@Composable
internal fun StickerSetListScreen(
    archived: Boolean,
    state: StickerSetListUiState,
    onEvent: (StickerSetListEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSet: (setId: Long) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(listTexts(archived).title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (state) {
                StickerSetListUiState.Loading -> {
                    SettingsLoading(
                        description = stringResource(R.string.settings_stickers_loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is StickerSetListUiState.Error -> {
                    SettingsError(
                        error = state.error,
                        onRetry = { onEvent(StickerSetListEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is StickerSetListUiState.Content -> {
                    if (state.sets.isEmpty()) {
                        StickerSetListEmpty(archived, Modifier.padding(contentPadding))
                    } else {
                        StickerSetList(archived, state, onEvent, onOpenSet, listState, contentPadding)
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList") // The page's kind, state and events, where a set opens, and the list's state.
@Composable
private fun StickerSetList(
    archived: Boolean,
    state: StickerSetListUiState.Content,
    onEvent: (StickerSetListEvent) -> Unit,
    onOpenSet: (setId: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style = SettingsListRowStyle(MaterialTheme.sizes.contentMaxWidth, windowMargin(), MaterialTheme.motionScheme)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(state.sets, key = { _, set -> set.id }, contentType = { _, _ -> ROW }) { index, set ->
            AvailableSetRow(
                set = set,
                shapes = ListItemDefaults.segmentedShapes(index, state.sets.size),
                onAdd = { onEvent(StickerSetListEvent.Add(set.id)) },
                onOpen = { onOpenSet(set.id) },
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        item(key = FOOTER, contentType = FOOTER) {
            SettingsFooter(stringResource(listTexts(archived).footer), Modifier.settingsListRow(this, style))
        }
    }
}

/**
 * A set the user may add, which a tap opens ([onOpen]): "Add" (named with the set for screen readers), progress while
 * adding, then a check.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AvailableSetRow(
    set: StickerSetItem,
    shapes: ListItemShapes,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { StickerCover(set.cover, MaterialTheme.sizes.leadingIconContainer) },
        supportingContent = { Text(stickerCount(set.count)) },
        trailingContent = {
            when {
                set.isInstalled -> {
                    val added = stringResource(R.string.settings_stickers_added_named, set.title)
                    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_check),
                            contentDescription = added,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                set.isBusy -> {
                    val adding = stringResource(R.string.settings_stickers_adding, set.title)
                    LoadingIndicator(Modifier.semantics { contentDescription = adding })
                }

                else -> {
                    val add = stringResource(R.string.settings_stickers_add_named, set.title)
                    TextButton(
                        onClick = onAdd,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.semantics { contentDescription = add },
                    ) {
                        Text(stringResource(R.string.settings_stickers_add))
                    }
                }
            }
        },
        colors = settingsRowColors(),
    ) {
        Text(text = set.title, style = LocalTextStyle.current.userContent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StickerSetListEmpty(
    archived: Boolean,
    modifier: Modifier = Modifier,
) {
    val texts = listTexts(archived)
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = texts.icon,
        title = stringResource(texts.emptyTitle),
        body = stringResource(texts.emptyBody),
        modifier = modifier,
    )
}

/** The page's title, footer, empty state and icon, for the archived or the trending sets. */
private class ListTexts(
    val title: Int,
    val footer: Int,
    val emptyTitle: Int,
    val emptyBody: Int,
    val icon: Int,
)

private fun listTexts(archived: Boolean) =
    if (archived) {
        ListTexts(
            title = R.string.settings_stickers_archived,
            footer = R.string.settings_stickers_archived_footer,
            emptyTitle = R.string.settings_stickers_archived_empty_title,
            emptyBody = R.string.settings_stickers_archived_empty_body,
            icon = UiR.drawable.ic_archive,
        )
    } else {
        ListTexts(
            title = R.string.settings_stickers_trending,
            footer = R.string.settings_stickers_trending_footer,
            emptyTitle = R.string.settings_stickers_trending_empty_title,
            emptyBody = R.string.settings_stickers_trending_empty_body,
            icon = R.drawable.ic_trending_up,
        )
    }

@PingogramPreviews
@Composable
private fun TrendingStickersScreenPreview() {
    PingogramPreview {
        StickerSetListScreen(archived = false, state = StickersPreviewStates.trending, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ArchivedStickersScreenPreview() {
    PingogramPreview {
        StickerSetListScreen(archived = true, state = StickersPreviewStates.archived, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun ArchivedStickersScreenEmptyPreview() {
    PingogramPreview {
        StickerSetListScreen(archived = true, state = StickersPreviewStates.noArchived, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun TrendingStickersScreenErrorPreview() {
    PingogramPreview {
        StickerSetListScreen(
            archived = false,
            state = StickerSetListUiState.Error(DomainError.Network),
            onEvent = {},
            onBack = {},
        )
    }
}

private const val ROW = "row"
private const val FOOTER = "footer"
