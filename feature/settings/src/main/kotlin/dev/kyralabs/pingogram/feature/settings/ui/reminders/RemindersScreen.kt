package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.LocalChatLauncher
import dev.kyralabs.pingogram.core.ui.component.LocalMessageLauncher
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Reminders page (the owner's boards 29e, 29f): hoists [RemindersViewModel]. A reminder opens its chat at its
 * message through `core:ui`'s `LocalMessageLauncher` (`LocalChatLauncher`, at the chat's place, where only that is
 * provided); deleting shows a snackbar with "Undo" (long, for screen readers).
 */
@Composable
internal fun RemindersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val details = isDebuggable()
    val openMessage = LocalMessageLauncher.current
    val openChat = LocalChatLauncher.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RemindersEffect.OpenMessage -> {
                    openMessage?.invoke(effect.chatId, effect.messageId) ?: openChat?.invoke(effect.chatId)
                }

                RemindersEffect.Deleted -> {
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.reminders_deleted),
                                actionLabel = resources.getString(R.string.reminders_undo),
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(RemindersEvent.UndoDelete)
                    }
                }

                is RemindersEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, details)) }
                }
            }
        }
    }
    RemindersScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The Reminders page (`docs/DESIGN_SYSTEM.md` §6 "Message reminders"): a settings page titled "Reminders" with the sort
 * menu in its top bar (by time or by chat), "Kept on this phone only" after the phone symbol, then "Upcoming · 3" and
 * "Done" as segmented groups of reminders ([ReminderItem]); without any, an `EmptyState` on `Cookie12Sided` with the
 * alarm and how to set one.
 */
@Composable
internal fun RemindersScreen(
    state: RemindersUiState,
    onEvent: (RemindersEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.reminders_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = { if (state is RemindersUiState.Content) SortMenu(state.sort, onEvent) },
    ) {
        StateCrossfade(state, Modifier.fillMaxWidth(), contentKey = { it::class }, label = "RemindersPage") { shown ->
            when (shown) {
                RemindersUiState.Loading -> SettingsLoading(stringResource(R.string.reminders_loading))
                RemindersUiState.Empty -> RemindersEmpty()
                is RemindersUiState.Content -> RemindersContent(shown, onEvent)
            }
        }
    }
}

@Composable
private fun RemindersContent(
    state: RemindersUiState.Content,
    onEvent: (RemindersEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
        OnThisPhoneNote(Modifier.padding(horizontal = MaterialTheme.spacing.large))
        if (state.upcoming.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.reminders_upcoming, state.upcoming.size)) {
                state.upcoming.forEach { row -> item(key = row.key) { shapes -> ReminderItem(row, shapes, onEvent) } }
            }
        }
        if (state.done.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.reminders_done)) {
                state.done.forEach { row -> item(key = row.key) { shapes -> ReminderItem(row, shapes, onEvent) } }
            }
        }
    }
}

/**
 * A reminder: its chat's avatar in the chat's shape, the chat's name (user content), the time on a chip (the alarm, in
 * `error` once overdue; a check for one done, dimmed), the message in two lines and the note. A tap opens the chat; a
 * swipe marks it done or deletes it ([ReminderSwipe]), as its custom actions do for screen readers.
 */
@Composable
private fun ReminderItem(
    row: ReminderRow,
    shapes: ListItemShapes,
    onEvent: (RemindersEvent) -> Unit,
) {
    val doneLabel = stringResource(if (row.isDone) R.string.reminders_mark_not_done else R.string.reminders_mark_done)
    val deleteLabel = stringResource(R.string.reminders_delete)
    val done = { onEvent(RemindersEvent.ToggleDone(row.chatId, row.messageId)) }
    val delete = { onEvent(RemindersEvent.Delete(row.chatId, row.messageId)) }
    val title = row.chatTitle.ifEmpty { stringResource(R.string.reminders_unknown_chat) }
    ReminderSwipe(shapes.shape, ReminderSwipeLabels(doneLabel, deleteLabel), onDone = done, onDelete = delete) {
        SegmentedListItem(
            onClick = { onEvent(RemindersEvent.Open(row.chatId, row.messageId)) },
            shapes = shapes,
            colors = settingsRowColors(),
            modifier =
                Modifier.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(doneLabel) { done().let { true } },
                            CustomAccessibilityAction(deleteLabel) { delete().let { true } },
                        )
                },
            leadingContent = {
                Avatar(
                    photo = row.photo,
                    name = title,
                    id = row.chatId,
                    size = MaterialTheme.sizes.leadingIconContainer,
                    shape = row.avatarShape,
                    modifier = Modifier.alpha(if (row.isDone) DONE_ALPHA else 1f),
                )
            },
            supportingContent = { ReminderDetails(row) },
        ) {
            Text(title, style = LocalTextStyle.current.userContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The time on its chip, then the message and the note. */
@Composable
private fun ReminderDetails(row: ReminderRow) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (row.isOverdue) colors.error else colors.primary
            Icon(
                painter = painterResource(if (row.isDone) UiR.drawable.ic_check else UiR.drawable.ic_alarm),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = if (row.isDone) colors.onSurfaceVariant else tint,
            )
            Text(
                text = row.time.resolve(),
                color = if (row.isDone) colors.onSurfaceVariant else tint,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        row.preview?.let {
            Text(
                text = it.resolve(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
        if (row.note.isNotEmpty()) {
            Text(
                text = stringResource(R.string.reminders_note, row.note),
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.userContent,
            )
        }
    }
}

/** "Kept on this phone only", after the phone symbol. */
@Composable
private fun OnThisPhoneNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_smartphone),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.reminders_on_this_phone),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** No reminders: the alarm on `Cookie12Sided`, how to set one, and that they stay on this phone. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemindersEmpty() {
    // EmptyState scrolls itself, so it takes a set height inside the page's own scroll.
    Box(Modifier.fillMaxWidth().height(MaterialTheme.sizes.illustrationMaxSize * 2)) {
        EmptyState(
            illustration = MaterialShapes.Cookie12Sided,
            icon = UiR.drawable.ic_alarm,
            title = stringResource(R.string.reminders_empty_title),
            body = stringResource(R.string.reminders_empty_body),
        )
    }
}

/** The sort menu: by time or by chat, the chosen one checked, in an expressive `DropdownMenuPopup`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortMenu(
    sort: ReminderSort,
    onEvent: (RemindersEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.reminders_sort))
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                ReminderSort.entries.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onEvent(RemindersEvent.Sort(option))
                        },
                        text = { Text(stringResource(option.label)) },
                        modifier = Modifier.semantics { selected = option == sort },
                        shape = MenuDefaults.itemShape(index, ReminderSort.entries.size).shape,
                        trailingContent = {
                            if (option == sort) {
                                Icon(
                                    painter = painterResource(UiR.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private val ReminderSort.label: Int
    get() = if (this == ReminderSort.ByTime) R.string.reminders_sort_time else R.string.reminders_sort_chat

private const val DONE_ALPHA = 0.6f
