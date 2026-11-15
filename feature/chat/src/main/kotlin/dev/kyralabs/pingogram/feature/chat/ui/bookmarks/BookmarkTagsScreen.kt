package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.dragHandle
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.component.rememberListReorderState
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Editing the bookmarks' tags: hoists [BookmarkTagsViewModel] and shows its snackbars; [onBack] leaves it. */
@Composable
internal fun BookmarkTagsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarkTagsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val details = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is BookmarkTagsEffect.Deleted -> resources.getString(R.string.bookmark_tag_deleted, effect.name)
                    is BookmarkTagsEffect.ShowError -> errorMessage(resources, effect.error, details)
                }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    BackHandler(enabled = (state as? BookmarkTagsUiState.Content)?.draft != null) {
        viewModel.onEvent(BookmarkTagsEvent.CancelDraft)
    }
    BookmarkTagsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Editing the bookmarks' tags (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34c): a small `TopAppBar`
 * with back, "Edit tags" and "New tag" (`TextButton` with the add symbol) on `surfaceContainer`, then on a
 * `ContentSheet`, at most `contentMaxWidth`, the tags in the user's order ([BookmarkTagRow], dragged by their handles,
 * the others sliding on the motion scheme's springs), a new tag's or a renamed tag's editor in its place
 * ([BookmarkTagEditor]), and what the order is for; no tags, an `EmptyState`. Deleting asks first
 * ([DeleteTagDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkTagsScreen(
    state: BookmarkTagsUiState,
    onEvent: (BookmarkTagsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? BookmarkTagsUiState.Content
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bookmark_tags_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.bookmarks_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onEvent(BookmarkTagsEvent.StartNew) },
                        enabled = content != null && content.draft == null,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painterResource(UiR.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(
                            text = stringResource(R.string.bookmark_new_tag),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        ContentSheet(innerPadding = padding) { contentPadding ->
            when {
                content == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
                }

                content.tags.isEmpty() && content.draft == null -> {
                    TagsEmpty()
                }

                else -> {
                    TagList(content, onEvent, contentPadding)
                }
            }
        }
    }
    content?.deleting?.let { tag -> DeleteTagDialog(tag, onEvent) }
}

@Composable
private fun TagList(
    content: BookmarkTagsUiState.Content,
    onEvent: (BookmarkTagsEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val reorder =
        rememberListReorderState(listState, content.tags.map { it.id }, rowKey = { it }) {
            onEvent(BookmarkTagsEvent.Reorder(it))
        }
    val byId = content.tags.associateBy { it.id }
    val rows = reorder.order.mapNotNull(byId::get)
    val motion = rememberItemMotion()
    val draft = content.draft
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            if (draft != null && draft.id == null) {
                item(key = NEW_KEY, contentType = EDITOR) {
                    BookmarkTagEditor(draft, onEvent, Modifier.animateItem(this, motion).rowPadding())
                }
            }
            itemsIndexed(rows, key = { _, tag -> tag.id }, contentType = { _, _ -> ROW }) { index, tag ->
                val dragged = reorder.draggedId == tag.id
                val itemModifier =
                    Modifier
                        // The dragged row follows the finger, not a placement spring.
                        .animateItem(this, motion, placement = !dragged)
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) reorder.offset else 0f }
                        .rowPadding()
                if (draft?.id == tag.id) {
                    BookmarkTagEditor(draft, onEvent, itemModifier)
                } else {
                    BookmarkTagRow(
                        tag = tag,
                        moves =
                            TagMoves(index > 0, index < rows.lastIndex) { up ->
                                onEvent(BookmarkTagsEvent.Move(tag.id, up))
                            },
                        dragged = dragged,
                        handleModifier = Modifier.dragHandle(reorder, tag.id),
                        onEvent = onEvent,
                        modifier = itemModifier,
                    )
                }
            }
            item(key = FOOTER, contentType = FOOTER) { TagsFooter(Modifier.animateItem(this, motion)) }
        }
    }
}

/** What the tags' order is for, and that they stay on this phone. */
@Composable
private fun TagsFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.bookmark_tags_footer),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Modifier.rowPadding(): Modifier = padding(horizontal = MaterialTheme.spacing.small)

/** No tags yet: the label on `Cookie9Sided`, what tags are for, and "New tag". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TagsEmpty() {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_bookmark_filled,
        title = stringResource(R.string.bookmark_tags_empty_title),
        body = stringResource(R.string.bookmark_tags_empty_body),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val NEW_KEY = "new"
private const val EDITOR = "editor"
private const val ROW = "row"
private const val FOOTER = "footer"
