package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The members page of a group or channel (`REQUIREMENTS.md` FR-P3; `docs/DESIGN_SYSTEM.md` §6 "Group and channel
 * info"): a settings-style page ([InfoPageScaffold]) titled by its filter, the search bar's input field on its
 * container (not for administrators), the filters as `FilterChip`s in a row that scrolls sideways where they do not
 * fit, then the members as one segmented group ([MemberRow]) under their count, more loading as the end comes near.
 * Loading is a delayed indicator, nobody found an [EmptyState] on `Cookie9Sided`, a failure one on `Cookie4Sided` with
 * "Try again". Removing a member asks with an `AlertDialog`.
 */
@Composable
internal fun ChatMembersScreen(
    state: ChatMembersUiState,
    onEvent: (ChatMembersEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    InfoPageScaffold(
        title = stringResource(state.filter.titleFor(state.kind)),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        InfoPageBody(innerPadding) { contentPadding ->
            MembersBody(state, onEvent, contentPadding, listState)
        }
    }
    state.removing?.let { member ->
        val name = member.name.resolve().bidiIsolated()
        ConfirmationDialog(
            title = stringResource(R.string.chat_info_remove_title, name).bidiIsolated(),
            text =
                stringResource(
                    if (state.kind == GroupKind.Channel) {
                        R.string.chat_info_remove_text_channel
                    } else {
                        R.string.chat_info_remove_text
                    },
                ),
            confirm = stringResource(R.string.chat_info_member_remove),
            onConfirm = { onEvent(ChatMembersEvent.ConfirmRemove) },
            onDismiss = { onEvent(ChatMembersEvent.DismissDialog) },
        )
    }
}

/** The search field and the filters at the top, the list (or its state) below them, as wide as a column gets. */
@Composable
private fun MembersBody(
    state: ChatMembersUiState,
    onEvent: (ChatMembersEvent) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val margin = windowMargin()
    val sides = remember(contentPadding) { SidesOf(contentPadding) }
    Column(
        modifier = Modifier.fillMaxSize().padding(sides),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = margin),
        ) {
            if (state.canSearch) {
                MembersSearchField(state.query, onQueryChange = { onEvent(ChatMembersEvent.Search(it)) })
            }
            if (state.filters.size > 1) FilterRow(state, onEvent)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val list = state.list) {
                MembersList.Loading -> {
                    val description = stringResource(R.string.chat_info_members_loading)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                MembersList.Empty -> {
                    MembersEmpty(state)
                }

                is MembersList.Error -> {
                    MembersError(list, onRetry = { onEvent(ChatMembersEvent.Retry) })
                }

                is MembersList.Content -> {
                    MembersLazyList(state, list, onEvent, contentPadding, listState)
                }
            }
        }
    }
}

/**
 * The search bar's input field, as the contacts list's, on the rows' container (the bar's own is the page's color),
 * with a clear button while there is text; it keeps its own text, starting from [query], and reports each change.
 */
@Composable
private fun MembersSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        // On the rows' container: the search bar's own is the page's color here.
        color = profileRowContainerColor(),
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.chat_info_members_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.chat_info_members_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
        )
    }
}

/** The filters as `FilterChip`s with their expressive shapes, scrolling sideways where they do not fit. */
@Composable
private fun FilterRow(
    state: ChatMembersUiState,
    onEvent: (ChatMembersEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        state.filters.forEach { filter ->
            FilterChip(
                selected = filter == state.filter,
                onClick = { onEvent(ChatMembersEvent.SelectFilter(filter)) },
                label = { Text(stringResource(filter.chipFor(state.kind))) },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** The members under their count, one segmented group, the next page loading or failing at its end. */
@Composable
private fun MembersLazyList(
    state: ChatMembersUiState,
    list: MembersList.Content,
    onEvent: (ChatMembersEvent) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val margin = windowMargin()
    val spacing = MaterialTheme.spacing
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val rowModifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = margin)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(CHAT_MEMBERS_LIST_TEST_TAG),
        state = listState,
        // The sides are the column's; the bottom (system bars) is read in the layout phase.
        contentPadding =
            remember(contentPadding, spacing) {
                contentPadding.withTop(spacing.small).plusBottom(spacing.extraLarge).let(::BottomAndTopOf)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            ProfileSectionHeader(
                title =
                    pluralStringResource(
                        if (state.kind == GroupKind.Channel) {
                            R.plurals.chat_info_subscribers
                        } else {
                            R.plurals.chat_info_members
                        },
                        list.totalCount,
                        numbers.format(list.totalCount),
                    ),
                modifier = rowModifier,
            )
        }
        itemsIndexed(list.items, key = { _, item -> item.key }, contentType = { _, _ -> ROW }) { index, item ->
            MemberRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, list.items.size),
                onClick = { onEvent(ChatMembersEvent.Open(item.sender)) },
                onAction = { action -> onEvent(ChatMembersEvent.Act(item.sender, action)) },
                modifier = rowModifier.animateItem(),
            )
        }
        when (val more = list.more) {
            MoreMembers.Loading -> {
                item(key = MORE, contentType = MORE) { MoreLoading() }
            }

            is MoreMembers.Failed -> {
                item(key = MORE, contentType = MORE) {
                    MoreFailed(more, onRetry = { onEvent(ChatMembersEvent.LoadMore) }, modifier = rowModifier)
                }
            }

            MoreMembers.None, MoreMembers.Available -> {}
        }
    }
    LoadMoreEffect(listState, canLoadMore = list.more == MoreMembers.Available) { onEvent(ChatMembersEvent.LoadMore) }
}

/** The next page on its way: a loading indicator, which screen readers hear. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreLoading() {
    val description = stringResource(R.string.chat_info_members_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** Why the next page failed, with "Try again". */
@Composable
private fun MoreFailed(
    more: MoreMembers.Failed,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = errorText(more.error),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.profile_retry)) }
    }
}

/** Nobody matches: an [EmptyState] on `Cookie9Sided`, which says whether a search found nothing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MembersEmpty(state: ChatMembersUiState) {
    val searched = state.canSearch && state.query.isNotBlank()
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_contacts,
        title =
            stringResource(
                if (searched) R.string.chat_info_members_none_found else R.string.chat_info_members_empty,
            ),
        body =
            if (searched) {
                stringResource(
                    R.string.chat_info_members_none_found_body,
                    state.query.trim().bidiIsolated(),
                ).bidiIsolated()
            } else {
                stringResource(R.string.chat_info_members_empty_body)
            },
    )
}

/** The first page failed: an [EmptyState] on `Cookie4Sided` in `errorContainer`, with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MembersError(
    error: MembersList.Error,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_info_members_error),
        body = errorText(error.error),
        containerColor = colors.errorContainer,
        iconColor = colors.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/**
 * While [canLoadMore], calls [onLoadMore] when the end of the list comes within [LOAD_MORE_THRESHOLD] items, and again
 * whenever items were added and the end is still near; read from the list's layout, outside its items.
 */
@Composable
internal fun LoadMoreEffect(
    listState: LazyListState,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnLoadMore() }
    }
}

/** The test tag of the members list, for tests that scroll it. */
internal const val CHAT_MEMBERS_LIST_TEST_TAG = "chat_members_list"

private const val HEADER = "header"
private const val ROW = "row"
private const val MORE = "more"

/** How close to the end of the list the next page is asked for, as Blocked users does. */
private const val LOAD_MORE_THRESHOLD = 5
