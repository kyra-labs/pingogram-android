package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Who saw the user's own story (board 26): a sheet with "24 viewers" in `titleLargeEmphasized` (a heading), the search
 * bar's field ("Search viewers"), All / Reactions / Contacts as `FilterChip`s (one radio group), then the viewers as
 * `ListItem`s: the 40 dp avatar, the name (user content) over when they saw it, and their reaction at the end in
 * `titleLarge`, one item for screen readers. More load 5 rows before the end; a `DelayedLoadingIndicator` before the
 * first, a failure with "Try again"; then the note that Telegram keeps the list for a day after the story ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryViewersSheet(
    state: StoryViewersUiState,
    onEvent: (StoryViewersEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    PingogramBottomSheet(onDismissRequest = onDismiss) {
        StoryViewersContent(state, onEvent)
    }
}

@Composable
private fun StoryViewersContent(
    state: StoryViewersUiState,
    onEvent: (StoryViewersEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = resources.getQuantityString(R.plurals.story_viewers_title, state.total, state.total),
            modifier = Modifier.padding(horizontal = spacing.large).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        ViewersSearchField(state.query, onQueryChange = { onEvent(StoryViewersEvent.Query(it)) })
        ViewersFilters(state, onEvent)
        Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.avatarLarge), Alignment.Center) {
            when {
                state.rows.isEmpty() && state.error != null -> {
                    TextButton(onClick = { onEvent(StoryViewersEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.story_viewers_try_again))
                    }
                }

                state.rows.isEmpty() && !state.hasLoaded -> {
                    DelayedLoadingIndicator()
                }

                state.rows.isEmpty() -> {
                    val empty =
                        if (state.query.isBlank()) R.string.story_viewers_empty else R.string.story_viewers_none_found
                    Text(
                        stringResource(empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    ViewersList(state, onEvent)
                }
            }
        }
        Text(
            text = stringResource(R.string.story_viewers_note),
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewersSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.story_viewers_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.new_story_people_clear),
                            )
                        }
                    }
                } else {
                    null
                },
        )
    }
}

/** All, Reactions and Contacts as `FilterChip`s, one radio group for screen readers. */
@Composable
private fun ViewersFilters(
    state: StoryViewersUiState,
    onEvent: (StoryViewersEvent) -> Unit,
) {
    val filters =
        listOf(
            StoryViewersFilter.All to stringResource(R.string.story_viewers_all, state.total),
            StoryViewersFilter.Reactions to stringResource(R.string.story_viewers_reactions, state.reactionTotal),
            StoryViewersFilter.Contacts to stringResource(R.string.story_viewers_contacts),
        )
    LazyRow(
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(filters, key = { it.first }) { (filter, label) ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { onEvent(StoryViewersEvent.Filter(filter)) },
                label = { Text(label) },
                shapes = FilterChipDefaults.shapes(),
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/** The viewers, asking for more 5 rows before the end. */
@Composable
private fun ViewersList(
    state: StoryViewersUiState,
    onEvent: (StoryViewersEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val nearEnd by remember(listState) {
        derivedStateOf {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(listState, state.hasMore) {
        if (state.hasMore) {
            snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect {
                currentOnEvent(StoryViewersEvent.LoadMore)
            }
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        items(state.rows, key = { it.userId }) { row -> ViewerRow(row) }
    }
}

@Composable
private fun ViewerRow(row: StoryViewerRow) {
    // "2 hours ago" (board 26), written out as screen readers hear it.
    val age = storyAgeSpoken(row.ageSeconds, LocalConfiguration.current.locales[0])
    val description =
        if (row.reaction != null) {
            stringResource(R.string.story_viewer_reacted, row.name, age, row.reaction)
        } else {
            stringResource(R.string.story_viewer_row, row.name, age)
        }
    ListItem(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        supportingContent = { Text(age) },
        leadingContent = {
            Avatar(photo = row.photo, name = row.name, id = row.userId, size = MaterialTheme.sizes.avatarSmall)
        },
        trailingContent = row.reaction?.let { { Text(it, style = MaterialTheme.typography.titleLarge) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge.userContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How many rows before the end the next page is asked for. */
private const val LOAD_MORE_AHEAD = 5

@PingogramPreviews
@Composable
private fun StoryViewersContentPreview() {
    PingogramPreview {
        StoryViewersContent(
            StoryViewersUiState(
                total = 24,
                reactionTotal = 6,
                hasLoaded = true,
                rows =
                    persistentListOf(
                        StoryViewerRow(1, "Aisha Khan", null, PREVIEW_TWO_HOURS, "❤️"),
                        StoryViewerRow(2, "Meera Iyer", null, PREVIEW_AN_HOUR, null),
                    ),
            ),
            onEvent = {},
        )
    }
}

private const val PREVIEW_TWO_HOURS = 7_200L
private const val PREVIEW_AN_HOUR = 3_600L
