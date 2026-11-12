package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Saved Messages filtered by a tag (the owner's board 8): a small `TopAppBar` with back ("Show all saved messages"),
 * "Saved Messages" over "3 tagged “❤ Ideas”" (`bodySmall`, a polite live region) and "Rename tag", the [chips] below
 * it, then on a `ContentSheet` the tagged messages, newest first, as list items inset like the chat list's rows: what
 * each says (`bodyLarge`, two lines, user content) over the day it was saved; a tap shows it in the conversation
 * ([onOpen]). More load near the end; loading, nothing and a failure (with "Try again") as the other lists show them.
 * Back clears the filter: its caller's ([SavedMessagesConversation]), which previews "All" with the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedTagScreen(
    state: SavedTagsUiState,
    tag: SavedTagChip,
    chips: @Composable () -> Unit,
    onEvent: (SavedTagEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            Column {
                TopAppBar(
                    title = { TagTitle(state, tag) },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(SavedTagEvent.ShowAll) }) {
                            Icon(
                                painterResource(UiR.drawable.ic_arrow_back),
                                stringResource(R.string.saved_tags_show_all),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { onEvent(SavedTagEvent.Rename(tag)) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.saved_tags_rename))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
                chips()
            }
        },
    ) { padding ->
        ContentSheet(padding, Modifier.consumeWindowInsets(padding)) { contentPadding ->
            TaggedList(state.results, onEvent, contentPadding)
        }
    }
    state.rename?.let { RenameTagDialog(it, onEvent) }
}

@Composable
private fun TagTitle(
    state: SavedTagsUiState,
    tag: SavedTagChip,
) {
    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            stringResource(R.string.saved_tags_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val count = state.results.total.takeIf { it > 0 } ?: tag.count
        Text(
            pluralStringResource(R.plurals.saved_tags_tagged, count, count, tag.name()),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Which of [TaggedContent]'s states [this] is, the content the crossfade tells apart. */
private val TaggedResults.kind: Int
    get() =
        when {
            items.isNotEmpty() -> 0
            error != null -> 1
            isLoading -> 2
            else -> 3
        }

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaggedList(
    results: TaggedResults,
    onEvent: (SavedTagEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, results.hasMore) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.distinctUntilChanged()
            .filter { it >= listState.layoutInfo.totalItemsCount - LOAD_AHEAD }
            .collect { onEvent(SavedTagEvent.LoadMore) }
    }
    // An error, loading, nothing tagged and the list crossfade in place; pages within the list do not.
    StateCrossfade(targetState = results, contentKey = { it.kind }, label = "taggedList") { shown ->
        TaggedContent(shown, onEvent, contentPadding, listState)
    }
}

/** What [TaggedList] shows for [results]: its error, loading, nothing tagged, or the list in [listState]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaggedContent(
    results: TaggedResults,
    onEvent: (SavedTagEvent) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    when {
        results.items.isEmpty() && results.error != null -> {
            ErrorRow(results, onEvent, Modifier.padding(MaterialTheme.spacing.medium))
        }

        results.items.isEmpty() && results.isLoading -> {
            DelayedLoadingIndicator(Modifier.fillMaxSize())
        }

        results.items.isEmpty() -> {
            Text(
                stringResource(R.string.saved_tags_empty),
                modifier = Modifier.padding(MaterialTheme.spacing.large),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            // Pages that load, and messages untagged meanwhile, come and go on the scheme's springs.
            val motion = rememberItemMotion()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                items(results.items, key = { it.messageId }, contentType = { "tagged" }) { item ->
                    TaggedRow(item, Modifier.animateItem(this, motion)) { onEvent(SavedTagEvent.Open(item.messageId)) }
                }
                if (results.isLoading) {
                    item(key = "loading", contentType = "loading") {
                        LoadingIndicator(
                            Modifier.animateItem(this, motion).fillMaxWidth().padding(MaterialTheme.spacing.small),
                        )
                    }
                } else if (results.error != null) {
                    item(key = "error", contentType = "error") {
                        ErrorRow(results, onEvent, Modifier.animateItem(this, motion))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaggedRow(
    item: TaggedItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val date =
        remember(item.date) {
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .format(Instant.ofEpochSecond(item.date).atZone(ZoneId.systemDefault()))
        }
    ListItem(
        onClick = onClick,
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.small),
        supportingContent = { Text(date, style = MaterialTheme.typography.bodyMedium.userContent) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            item.preview.resolve(),
            style = MaterialTheme.typography.bodyLarge.userContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorRow(
    results: TaggedResults,
    onEvent: (SavedTagEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = results.error ?: return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            errorMessage(LocalResources.current, error, showDetails = false),
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = { onEvent(SavedTagEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.saved_tags_retry))
        }
    }
}

/** What the user does on Saved Messages' tags. */
internal sealed interface SavedTagEvent {
    data object ShowAll : SavedTagEvent

    data object LoadMore : SavedTagEvent

    data object Retry : SavedTagEvent

    data class Open(
        val messageId: Long,
    ) : SavedTagEvent

    data class Rename(
        val tag: SavedTagChip,
    ) : SavedTagEvent

    data class SaveLabel(
        val label: String,
    ) : SavedTagEvent

    data object DismissRename : SavedTagEvent
}

private const val LOAD_AHEAD = 5

internal val savedTagPreview = SavedTagChip(ReactionType.Emoji("❤"), "Ideas", 3)

internal val savedTagsPreview =
    SavedTagsUiState(
        tags =
            persistentListOf(
                savedTagPreview,
                SavedTagChip(ReactionType.Emoji("👍"), "Read later", 7),
                SavedTagChip(ReactionType.Emoji("⭐"), "", 4),
            ),
        selected = savedTagPreview,
        results =
            TaggedResults(
                items =
                    persistentListOf(
                        TaggedItem(
                            3,
                            UiText.Plain("Folders that swipe like a pager, the neighbours loaded ahead."),
                            1_762_000_000,
                        ),
                        TaggedItem(2, UiText.Plain("Use this for the FAB morph on scroll."), 1_761_900_000),
                        TaggedItem(1, UiText.Plain("MaterialShapes are built in now."), 1_761_800_000),
                    ),
                total = 3,
            ),
    )

@PingogramPreviews
@Composable
private fun SavedTagScreenPreview() {
    PingogramPreview {
        SavedTagScreen(
            savedTagsPreview,
            savedTagPreview,
            chips = { SavedTagChips(savedTagsPreview, {}, {}) },
            onEvent = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun SavedTagScreenLoadingPreview() {
    PingogramPreview {
        val state = savedTagsPreview.copy(results = TaggedResults(isLoading = true))
        SavedTagScreen(state, savedTagPreview, chips = { SavedTagChips(state, {}, {}) }, onEvent = {})
    }
}

@PingogramPreviews
@Composable
private fun SavedTagScreenEmptyPreview() {
    PingogramPreview {
        val state = savedTagsPreview.copy(results = TaggedResults())
        SavedTagScreen(state, savedTagPreview, chips = { SavedTagChips(state, {}, {}) }, onEvent = {})
    }
}

@PingogramPreviews
@Composable
private fun SavedTagRenamePreview() {
    PingogramPreview {
        SavedTagScreen(savedTagsPreview.copy(rename = SavedTagRename(savedTagPreview)), savedTagPreview, chips = {
        }, onEvent = {})
    }
}
