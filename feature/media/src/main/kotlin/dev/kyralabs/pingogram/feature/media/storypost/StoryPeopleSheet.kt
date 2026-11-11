package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Selected people" (`docs/DESIGN_SYSTEM.md` §6 "New story: who sees it"): a [ModalBottomSheet] of the user's contacts,
 * those in [initial] picked, to choose who sees the story. "Done" closes it, then hands the picks to [onDone]; the
 * scrim, dragging, back and "Cancel" close it without a change ([onDismiss]). The picks are forgotten once it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryPeopleSheet(
    initial: List<Long>,
    onDone: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StoryPeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start(initial) }
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val close: (List<Long>?) -> Unit = { picked ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (sheetState.currentValue == SheetValue.Hidden) {
                viewModel.reset()
                if (picked != null) currentOnDone(picked) else currentOnDismiss()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            currentOnDismiss()
        },
        sheetState = sheetState,
    ) {
        StoryPeopleContent(state, query) { event ->
            when (event) {
                is StoryPeopleEvent.Query -> viewModel.setQuery(event.text)
                is StoryPeopleEvent.Toggle -> viewModel.toggle(event.userId)
                StoryPeopleEvent.Done -> close(viewModel.picked)
                StoryPeopleEvent.Cancel -> close(null)
            }
        }
    }
}

/**
 * The sheet's content: the title, who sees the story, the search bar's field, the contacts as toggleable rows (a
 * picked one `large` in `secondaryContainer`, its avatar turned into a check), then "Cancel" at the start and "Done"
 * at the end, enabled once someone is picked. Loading, failing and having nobody to pick crossfade in the rows' place.
 */
@Composable
internal fun StoryPeopleContent(
    state: StoryPeopleUiState,
    query: String,
    modifier: Modifier = Modifier,
    onEvent: (StoryPeopleEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.new_story_people_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(
                        horizontal = spacing.large,
                    ).padding(bottom = spacing.small)
                    .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.new_story_people_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.large).padding(bottom = spacing.medium),
        )
        SearchField(query) { onEvent(StoryPeopleEvent.Query(it)) }
        Body(state, onToggle = { onEvent(StoryPeopleEvent.Toggle(it)) }, modifier = Modifier.weight(1f, fill = false))
        Actions(
            picked = (state as? StoryPeopleUiState.Content)?.pickedCount ?: 0,
            onDone = { onEvent(StoryPeopleEvent.Done) },
            onCancel = { onEvent(StoryPeopleEvent.Cancel) },
        )
    }
}

/** What the sheet's user does. */
internal sealed interface StoryPeopleEvent {
    data class Query(
        val text: String,
    ) : StoryPeopleEvent

    data class Toggle(
        val userId: Long,
    ) : StoryPeopleEvent

    data object Done : StoryPeopleEvent

    data object Cancel : StoryPeopleEvent
}

/** The contacts, or why there are none, crossfading as the state changes. */
@Composable
private fun Body(
    state: StoryPeopleUiState,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(effects) togetherWith fadeOut(effects) using SizeTransform(sizeAnimationSpec = { _, _ -> spatial })
        },
        modifier = modifier,
        label = "storyPeople",
    ) { shown ->
        when (shown) {
            StoryPeopleUiState.Loading -> {
                Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.avatarProfile), Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            }

            is StoryPeopleUiState.Error -> {
                Note(errorText(shown.error), color = MaterialTheme.colorScheme.error)
            }

            is StoryPeopleUiState.Content -> {
                when {
                    shown.candidates.isNotEmpty() -> Candidates(shown, onToggle)
                    shown.noMatches -> Note(stringResource(R.string.new_story_people_no_matches))
                    else -> Note(stringResource(R.string.new_story_people_no_contacts))
                }
            }
        }
    }
}

/** "Cancel" at the start, "Done" (with how many are [picked]) at the end, enabled once someone is. */
@Composable
private fun Actions(
    picked: Int,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.new_story_people_cancel))
        }
        Button(onClick = onDone, enabled = picked > 0, shapes = ButtonDefaults.shapes()) {
            Text(
                text =
                    if (picked > 0) {
                        pluralStringResource(R.plurals.new_story_people_done_count, picked, picked)
                    } else {
                        stringResource(R.string.new_story_people_done)
                    },
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

@Composable
private fun Candidates(
    state: StoryPeopleUiState.Content,
    onToggle: (Long) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.candidates, key = { it.userId }, contentType = { "person" }) { candidate ->
            PersonRow(candidate, onToggle = { onToggle(candidate.userId) })
        }
    }
}

/** A contact: their avatar (a check while picked) and name; the whole row toggles them, as a checkbox. */
@Composable
private fun PersonRow(
    candidate: StoryPerson,
    onToggle: () -> Unit,
) {
    val picked = candidate.isPicked
    val shape = if (picked) MaterialTheme.shapes.large else MaterialTheme.shapes.extraSmall
    val color = if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val name = candidate.name.ifBlank { stringResource(R.string.new_story_people_someone) }
    ListItem(
        leadingContent = {
            Avatar(
                photo = candidate.photo,
                name = candidate.name,
                id = candidate.userId,
                size = MaterialTheme.sizes.avatarRow,
                selected = picked,
                onSelection = picked,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small)
                .clip(shape)
                .background(color)
                .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge.userContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Note(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.large,
            ),
    )
}

/** The search bar's input field on its container, with a clear button while there is text. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.new_story_people_search)) },
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}
