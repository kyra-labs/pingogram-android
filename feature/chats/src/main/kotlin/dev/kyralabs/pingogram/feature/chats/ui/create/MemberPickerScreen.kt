package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.CreateChatPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Picking people from the contacts for a new group or channel (`REQUIREMENTS.md` FR-P4, `docs/DESIGN_SYSTEM.md` §6,
 * "New group"), pushed above the New chat screen: a small top bar with back ([onBack]) and [title] on
 * `surfaceContainer`, and on the rounded `surface` sheet below
 * - the search bar's input field on its container, filtering as the user types;
 * - the people picked so far as removable [InputChip]s in a row that scrolls sideways, growing in with the first;
 * - the contacts as toggleable list items inset in the sheet like the forward sheet's: a picked one takes the list
 *   item's selected colors and `large` shape, and its avatar morphs into a check (`docs/DESIGN_SYSTEM.md` §4).
 *
 * A small extended floating action button, [action], goes on ([onAction]). While [isWorking] a wavy progress
 * indicator runs under the chips; a [floodWait] counts down there. The list is keyed and typed, and its rows run no
 * effects (`docs/PERFORMANCE.md` §4).
 */
@Suppress("LongParameterList") // The picker's state and events, the step's title, action and progress.
@Composable
internal fun MemberPickerScreen(
    state: MemberPickerUiState,
    title: String,
    action: String,
    onEvent: (MemberPickerEvent) -> Unit,
    onAction: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isWorking: Boolean = false,
    floodWait: FloodWait? = null,
) {
    CreateChatScaffold(
        title = title,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        floatingActionButton = { ActionButton(action, onAction) },
    ) { contentPadding ->
        val direction = LayoutDirection.Ltr
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val maxWidth = MaterialTheme.sizes.contentMaxWidth
            val margin = windowMargin()
            PickerSearchField(
                query = state.query,
                onQueryChange = { onEvent(MemberPickerEvent.QueryChanged(it)) },
                modifier = Modifier.padding(horizontal = margin).widthIn(max = maxWidth),
            )
            PickedChips(state.picked, onEvent, Modifier.widthIn(max = maxWidth).fillMaxWidth())
            if (isWorking) LinearWavyProgressIndicator(Modifier.padding(horizontal = margin).widthIn(max = maxWidth))
            floodWait?.let { FloodWaitNote(it, Modifier.padding(horizontal = margin).widthIn(max = maxWidth)) }
            ContactList(
                contacts = state.contacts,
                onEvent = onEvent,
                contentPadding =
                    PaddingValues(
                        start = contentPadding.calculateStartPadding(direction),
                        end = contentPadding.calculateEndPadding(direction),
                        // Clear of the floating action button.
                        bottom =
                            contentPadding.calculateBottomPadding() + MaterialTheme.sizes.fabSmall +
                                MaterialTheme.spacing.large,
                    ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The step's action as a small extended floating action button with a forward arrow, named by its label for screen
 * readers, as the extended button's text is not merged into it while it can collapse.
 */
@Composable
private fun ActionButton(
    action: String,
    onClick: () -> Unit,
) {
    SmallExtendedFloatingActionButton(
        text = {
            Text(action, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLargeEmphasized)
        },
        // The back arrow (mirrored for right to left) turned around.
        icon = { Icon(painterResource(UiR.drawable.ic_arrow_back), null, Modifier.scale(scaleX = -1f, scaleY = 1f)) },
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = action },
        expanded = true,
    )
}

/**
 * The search field: the search bar's input field (`SearchBarDefaults.InputField`) on the search bar's container, as
 * the contacts list and the forward sheet have it, with a clear button while there is text. It keeps its own text,
 * starting from [query], and reports each change to [onQueryChange].
 */
@Composable
private fun PickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.create_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.create_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
    }
}

/**
 * The people picked, as [InputChip]s with their avatar and a close symbol, in the order they were picked; a tap
 * removes one. The row grows in with the first and shrinks away with the last, and scrolls to a new one at its end.
 */
@Composable
private fun PickedChips(
    picked: ImmutableList<MemberItem>,
    onEvent: (MemberPickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var count = 0
        snapshotFlow { listState.layoutInfo.totalItemsCount }.collect { total ->
            if (total > count && total > 0) listState.animateScrollToItem(total - 1)
            count = total
        }
    }
    AnimatedVisibility(
        visible = picked.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val spacing = MaterialTheme.spacing
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = windowMargin()),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            items(picked, key = { it.userId }, contentType = { CHIP }) { member ->
                PickedChip(
                    member = member,
                    onRemove = { onEvent(MemberPickerEvent.Remove(member.userId)) },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                        ),
                )
            }
        }
    }
}

@Composable
private fun PickedChip(
    member: MemberItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeLabel = stringResource(R.string.create_member_remove, member.name)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = member.name,
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.mediaMinSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.userContent,
            )
        },
        shapes = InputChipDefaults.shapes(),
        modifier =
            modifier.semantics {
                contentDescription = removeLabel
                onClick(label = removeLabel) {
                    onRemove()
                    true
                }
            },
        avatar = {
            Avatar(photo = member.photo, name = member.name, id = member.userId, size = InputChipDefaults.AvatarSize)
        },
        trailingIcon = {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

/** The contacts of [contacts], or why there are none: loading, none at all, none matching, or an error. */
@Composable
private fun ContactList(
    contacts: MemberList,
    onEvent: (MemberPickerEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val motion = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (contacts) {
            MemberList.Loading -> {
                item(key = LOADING, contentType = LOADING) {
                    val description = stringResource(R.string.new_chat_loading)
                    Box(
                        modifier = Modifier.row(this, maxWidth, motion).padding(vertical = MaterialTheme.spacing.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }
            }

            MemberList.Empty -> {
                item(key = EMPTY, contentType = NOTE) {
                    ListNote(stringResource(R.string.create_no_contacts), Modifier.row(this, maxWidth, motion))
                }
            }

            MemberList.NoResults -> {
                item(key = NO_RESULTS, contentType = NOTE) {
                    ListNote(stringResource(R.string.create_no_results), Modifier.row(this, maxWidth, motion))
                }
            }

            is MemberList.Error -> {
                item(key = ERROR, contentType = ERROR) {
                    ListError(contacts, onEvent, Modifier.row(this, maxWidth, motion))
                }
            }

            is MemberList.Content -> {
                items(contacts.items, key = { it.userId }, contentType = { MEMBER }) { member ->
                    MemberRow(
                        member = member,
                        onToggle = { onEvent(MemberPickerEvent.Toggle(member.userId)) },
                        modifier = Modifier.row(this, maxWidth, motion),
                    )
                }
            }
        }
    }
}

// Keys and content types of the lists' entries; a contact's key is its user id.
private const val CHIP = "chip"
private const val MEMBER = "member"
private const val LOADING = "loading"
private const val NOTE = "note"
private const val EMPTY = "empty"
private const val NO_RESULTS = "noResults"
private const val ERROR = "error"

@PingogramPreviews
@Composable
private fun MemberPickerScreenPreview(
    @PreviewParameter(CreateChatPreviewStates.Members::class) state: MemberPickerUiState,
) {
    PingogramPreview {
        MemberPickerScreen(
            state = state,
            title = stringResource(R.string.new_chat_group),
            action = stringResource(R.string.create_next),
            onEvent = {},
            onAction = {},
            onBack = {},
        )
    }
}
