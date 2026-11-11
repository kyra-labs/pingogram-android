package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Start a call" (`docs/DESIGN_SYSTEM.md` §6 "Recent calls"): a [ModalBottomSheet] with "New group call" first, then
 * the user's contacts, each with a voice call button and, where the device has a camera, a video call button. A choice
 * closes it, then goes to [onChoose]; the scrim, dragging and back close it without one ([onDismiss]). The query is
 * forgotten once it closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartCallSheet(
    onChoose: (StartCallChoice) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StartCallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnChoose by rememberUpdatedState(onChoose)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            currentOnDismiss()
        },
        sheetState = sheetState,
    ) {
        StartCallContent(state, query, onQuery = viewModel::setQuery) { choice ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (sheetState.currentValue == SheetValue.Hidden) {
                    viewModel.reset()
                    currentOnChoose(choice)
                }
            }
        }
    }
}

/**
 * The sheet's content: the title, "New group call" as a row with its symbol in a `primaryContainer` circle, the search
 * bar's field, then the contacts ([PersonRow]). Loading, failing and having nobody to call crossfade in the rows'
 * place.
 */
@Composable
internal fun StartCallContent(
    state: StartCallUiState,
    query: String,
    modifier: Modifier = Modifier,
    onQuery: (String) -> Unit,
    onChoose: (StartCallChoice) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.start_call_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(horizontal = spacing.large)
                    .padding(bottom = spacing.small)
                    .semantics { heading() },
        )
        GroupCallRow(onClick = { onChoose(StartCallChoice.GroupCall) })
        SearchField(query, onQuery)
        Body(state, onChoose, Modifier.weight(1f, fill = false))
    }
}

/** "New group call": the group symbol in a `primaryContainer` circle at the avatars' size, over what it does. */
@Composable
private fun GroupCallRow(onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(MaterialTheme.sizes.avatarRow)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_group),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        supportingContent = { Text(stringResource(R.string.start_call_new_group_text)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClick = onClick),
    ) {
        Text(stringResource(R.string.start_call_new_group), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The contacts, or why there are none, crossfading as the state changes. */
@Composable
private fun Body(
    state: StartCallUiState,
    onChoose: (StartCallChoice) -> Unit,
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
        label = "startCall",
    ) { shown ->
        when (shown) {
            StartCallUiState.Loading -> {
                Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.avatarProfile), Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            }

            is StartCallUiState.Error -> {
                Note(errorText(shown.error), color = MaterialTheme.colorScheme.error)
            }

            is StartCallUiState.Content -> {
                when {
                    shown.people.isNotEmpty() -> People(shown, onChoose)
                    shown.noMatches -> Note(stringResource(R.string.call_invite_no_matches))
                    else -> Note(stringResource(R.string.start_call_no_contacts))
                }
            }
        }
    }
}

@Composable
private fun People(
    state: StartCallUiState.Content,
    onChoose: (StartCallChoice) -> Unit,
) {
    val motion = rememberItemMotion()
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.people, key = { it.userId }, contentType = { "person" }) { person ->
            // People come and go as the search filters them.
            Box(Modifier.animateItem(this, motion)) { PersonRow(person, state.canVideoCall, onChoose) }
        }
    }
}

/**
 * A contact: their avatar and name, then a voice call button and, with [canVideoCall], a video call button, each named
 * with the person for screen readers and with a tooltip.
 */
@Composable
private fun PersonRow(
    person: StartCallPerson,
    canVideoCall: Boolean,
    onChoose: (StartCallChoice) -> Unit,
) {
    val name = person.name.ifBlank { stringResource(R.string.call_someone) }
    ListItem(
        leadingContent = {
            Avatar(photo = person.photo, name = person.name, id = person.userId, size = MaterialTheme.sizes.avatarRow)
        },
        trailingContent = {
            Row {
                CallButton(
                    icon = UiR.drawable.ic_call,
                    label = stringResource(R.string.start_call_voice),
                    description = stringResource(R.string.start_call_voice_person, name.bidiIsolated()),
                    onClick = { onChoose(StartCallChoice.Person(person.userId, isVideo = false)) },
                )
                if (canVideoCall) {
                    CallButton(
                        icon = UiR.drawable.ic_videocam,
                        label = stringResource(R.string.start_call_video),
                        description = stringResource(R.string.start_call_video_person, name.bidiIsolated()),
                        onClick = { onChoose(StartCallChoice.Person(person.userId, isVideo = true)) },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge.userContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An icon button with a [PlainTooltip] of its [label]; screen readers hear [description]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallButton(
    icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), contentDescription = description)
        }
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
                .padding(top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.call_invite_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.call_invite_clear),
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
