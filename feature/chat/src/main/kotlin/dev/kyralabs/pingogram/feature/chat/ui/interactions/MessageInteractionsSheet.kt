package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReactionGlyph
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Who saw a group message and who reacted to it (`docs/DESIGN_SYSTEM.md` §6 "Seen by and reactions"), a
 * [ModalBottomSheet] as the conversation's other sheets, from the message's "Seen by" or "Reactions".
 *
 * - **Title**: "Seen by" on the viewers' tab, "Reactions" on the others, in `titleLargeEmphasized`, a heading.
 * - **Tabs** ([InteractionsTabRow]), where there is more than one: "Seen" and its count, "All" and each reaction with
 *   their counts, as the shared media's and the folder tabs: a connected button group of toggle buttons in a fading
 *   horizontal scroll.
 * - **Rows** inset like the chat list's: the 40 dp avatar, the name as user content, when they saw it or reacted, and
 *   on "All" the reaction at the end. A tap opens the profile and closes the sheet ([InteractionsEvent.Open]); screen
 *   readers hear each row as one ("Ada Lovelace, Seen at 9:41 AM").
 * - **States**, crossfading on the motion scheme's effects springs as the sheet resizes on its default spatial spring:
 *   the delayed loading indicator, an empty tab and a failed one (a `MaterialShapes` badge, a title and a line, "Try
 *   again"); the next page's loading indicator or failure below the rows.
 *
 * Dragging it down, the scrim and back close it ([InteractionsEvent.Dismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageInteractionsBottomSheet(
    sheet: MessageInteractionsSheet,
    onEvent: (InteractionsEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    // The sheet's window keeps the conversation's direction, also where only the composition sets it.
    val direction = LocalLayoutDirection.current
    ModalBottomSheet(onDismissRequest = { currentOnEvent(InteractionsEvent.Dismiss) }) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MessageInteractionsContent(sheet, currentOnEvent)
        }
    }
}

/** The title, the tabs and the selected tab's rows or why there are none. */
@Composable
internal fun MessageInteractionsContent(
    sheet: MessageInteractionsSheet,
    onEvent: (InteractionsEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motionScheme
    Column(Modifier.padding(bottom = spacing.large)) {
        val title =
            if (sheet.selected ==
                InteractionsTab.Seen
            ) {
                R.string.interactions_title_seen
            } else {
                R.string.interactions_title_reactions
            }
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(horizontal = spacing.large, vertical = spacing.small)
                    .semantics { heading() },
        )
        if (sheet.tabs.size > 1) {
            InteractionsTabRow(
                tabs = sheet.tabs,
                selected = sheet.selected,
                onSelect = { onEvent(InteractionsEvent.SelectTab(it)) },
                modifier = Modifier.padding(bottom = spacing.small),
            )
        }
        AnimatedContent(
            targetState = sheet,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it.selected to it.content.kind },
            label = "interactions",
        ) { shown ->
            when (val content = shown.content) {
                InteractionsContent.Loading -> InteractionsLoading()
                is InteractionsContent.Error -> InteractionsError(content) { onEvent(InteractionsEvent.Retry) }
                InteractionsContent.Empty -> InteractionsEmpty(isSeen = shown.selected == InteractionsTab.Seen)
                is InteractionsContent.Rows -> InteractionList(content, onEvent)
            }
        }
    }
}

/** The rows, then the next page's progress or failure; asks for the next page as the end comes near. */
@Composable
private fun InteractionList(
    content: InteractionsContent.Rows,
    onEvent: (InteractionsEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.testTag(INTERACTIONS_LIST_TAG)) {
        items(content.rows, key = { it.key }, contentType = { CONTENT_TYPE_ROW }) { row ->
            InteractionRowItem(row, onClick = { onEvent(InteractionsEvent.Open(row.sender)) })
        }
        when (val more = content.more) {
            MoreInteractions.Loading -> {
                item(key = KEY_MORE, contentType = KEY_MORE) { MoreLoading() }
            }

            is MoreInteractions.Failed -> {
                item(key = KEY_MORE, contentType = KEY_MORE) {
                    MoreFailed(more) { onEvent(InteractionsEvent.Retry) }
                }
            }

            MoreInteractions.None, MoreInteractions.Available -> {}
        }
    }
    LoadMoreEffect(listState, canLoadMore = content.more == MoreInteractions.Available) {
        onEvent(InteractionsEvent.LoadMore)
    }
}

/**
 * One person: their avatar and name, when they saw the message or reacted below it, the reaction at the end on the
 * tab of every reaction; a list item on the sheet's color inset like the chat list's rows, one item for screen readers.
 */
@Composable
private fun InteractionRowItem(
    row: InteractionRow,
    onClick: () -> Unit,
) {
    val name = row.name.resolve()
    val time = row.time?.resolve()
    val resources = LocalResources.current
    val insets = sheetRowInsets()
    ListItem(
        onClick = onClick,
        modifier =
            Modifier.padding(insets).semantics(mergeDescendants = true) {
                contentDescription =
                    listOfNotNull(
                        name,
                        row.reaction?.let {
                            resources.getString(R.string.interactions_reacted_with, it.spoken(resources))
                        },
                        time,
                    ).joinToString()
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = row.photo,
                name = name,
                id = row.avatarId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = row.placeholderIcon,
            )
        },
        supportingContent =
            time?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            },
        trailingContent = row.reaction?.let { label -> { RowReaction(label) } },
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.userContent,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The reaction at a row's end, as large as the bubble's badges show it. */
@Composable
private fun RowReaction(label: ReactionLabel) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.clearAndSetSemantics {}) {
        ReactionGlyph(
            label = label,
            style = MaterialTheme.typography.titleLarge,
            customEmojiSize = MaterialTheme.sizes.inlineIcon,
            placeholderColor = { color.copy(alpha = PLACEHOLDER_ALPHA) },
            contentColor = { color },
        )
    }
}

/**
 * While [canLoadMore], calls [onLoadMore] when the end of the list comes within [LOAD_MORE_THRESHOLD] rows, and again
 * whenever rows were added and the end is still near.
 */
@Composable
private fun LoadMoreEffect(
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
            // The item count, so that a page that leaves the end near asks again.
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnLoadMore() }
    }
}

/** What the sheet shows, for its crossfade: loading, a failure, nobody, or the rows (which change in place). */
private val InteractionsContent.kind: Int
    get() =
        when (this) {
            InteractionsContent.Loading -> KIND_LOADING
            is InteractionsContent.Error -> KIND_FAILED
            InteractionsContent.Empty -> KIND_EMPTY
            is InteractionsContent.Rows -> KIND_ROWS
        }

private const val KIND_LOADING = 0
private const val KIND_FAILED = 1
private const val KIND_EMPTY = 2
private const val KIND_ROWS = 3

/** The next page is asked for this many rows before the end, as the blocked users' list does. */
private const val LOAD_MORE_THRESHOLD = 5

/** A custom emoji's placeholder dot: a 38% tint of the row's content color, as on the reaction badges. */
private const val PLACEHOLDER_ALPHA = 0.38f

private const val CONTENT_TYPE_ROW = "row"
private const val KEY_MORE = "more"

/** The sheet's rows, for UI tests. */
internal const val INTERACTIONS_LIST_TAG = "interactions_list"

/**
 * The sheet of [viewModel] while it has one, and its effects: a user's profile through [onOpenProfile], a chat's (a
 * channel someone reacted as) through [onOpenChat]; nothing without them.
 */
@Composable
internal fun MessageInteractionsHost(
    viewModel: MessageInteractionsViewModel,
    onOpenProfile: ((userId: Long) -> Unit)?,
    onOpenChat: ((chatId: Long) -> Unit)?,
) {
    val currentOnOpenProfile by rememberUpdatedState(onOpenProfile)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is InteractionsEffect.OpenProfile -> currentOnOpenProfile?.invoke(effect.userId)
                is InteractionsEffect.OpenChat -> currentOnOpenChat?.invoke(effect.chatId)
            }
        }
    }
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    sheet?.let { MessageInteractionsBottomSheet(it, viewModel::onEvent) }
}
