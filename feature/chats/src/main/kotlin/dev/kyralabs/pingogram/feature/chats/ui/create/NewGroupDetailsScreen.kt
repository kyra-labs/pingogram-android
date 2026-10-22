package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.CreateChatPreviewStates

/**
 * The last step of creating a group (`REQUIREMENTS.md` FR-P4, `docs/DESIGN_SYSTEM.md` §6, "New group"): a small top
 * bar with back ([onBack]) and "New group", and on the sheet a form at most `contentMaxWidth` wide:
 * - the photo ([ChatPhotoButton], the system photo picker through [onPickPhoto]);
 * - the name in Material's rounded tonal field, required, and how many people will be added;
 * - "Auto-delete messages" as a connected button group (off, a day, a week, a month);
 * - the `FLOOD_WAIT` countdown or a [androidx.compose.material3.LinearWavyProgressIndicator] while the group is
 *   created, then the default [Button] "Create" at the end, with the press morph.
 *
 * Then, if Telegram did not add some of the people, the dialog about them ([NotAddedAlert]).
 */
@Composable
internal fun NewGroupDetailsScreen(
    state: NewGroupDetailsUiState,
    onEvent: (ChatDetailsEvent) -> Unit,
    onNotAddedEvent: (NotAddedEvent) -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val enabled = !state.isCreating
    val create = { onEvent(ChatDetailsEvent.Create) }
    CreateChatScaffold(
        title = stringResource(R.string.new_chat_group),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { contentPadding ->
        CreateChatForm(contentPadding) {
            ChatPhotoButton(
                photo = state.photo,
                description = stringResource(R.string.create_group_photo),
                onPick = onPickPhoto,
                onRemove = { onEvent(ChatDetailsEvent.RemovePhoto) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            CreateChatTextField(
                value = state.title,
                onValueChange = { onEvent(ChatDetailsEvent.TitleChanged(it)) },
                label = stringResource(R.string.create_group_name),
                error = stringResource(R.string.create_group_name_error).takeIf { state.titleError },
                supporting = memberCountText(state.memberCount),
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onImeAction = create,
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
            )
            AutoDeleteChoice(state.autoDelete, onEvent, enabled)
            FormEnd(floodWait = state.floodWait, isWorking = state.isCreating) {
                Button(onClick = create, enabled = state.canCreate, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.create_group), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
    state.notAdded?.let { NotAddedAlert(it, isChannel = false, onEvent = onNotAddedEvent) }
}

/** "3 people will be added", or that it is only the user for now. */
@Composable
private fun memberCountText(count: Int): String =
    if (count == 0) {
        stringResource(R.string.create_group_no_members)
    } else {
        pluralStringResource(R.plurals.create_group_members, count, count)
    }

/** How long messages stay: a header in `primary`, the choices, and what the choice means. */
@Composable
private fun AutoDeleteChoice(
    autoDelete: MessageAutoDelete,
    onEvent: (ChatDetailsEvent) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(R.string.create_auto_delete),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        ConnectedChoices(
            labels = MessageAutoDelete.entries.map { stringResource(it.label) },
            selectedIndex = autoDelete.ordinal,
            onSelect = { onEvent(ChatDetailsEvent.AutoDeleteChanged(MessageAutoDelete.entries[it])) },
            enabled = enabled,
        )
        val note =
            if (autoDelete == MessageAutoDelete.Off) {
                R.string.create_auto_delete_off_note
            } else {
                R.string.create_auto_delete_note
            }
        Text(
            text = stringResource(note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The label of each auto-delete time. */
private val MessageAutoDelete.label: Int
    get() =
        when (this) {
            MessageAutoDelete.Off -> R.string.create_auto_delete_off
            MessageAutoDelete.Day -> R.string.create_auto_delete_day
            MessageAutoDelete.Week -> R.string.create_auto_delete_week
            MessageAutoDelete.Month -> R.string.create_auto_delete_month
        }

@PingogramPreviews
@Composable
private fun NewGroupDetailsScreenPreview(
    @PreviewParameter(CreateChatPreviewStates.GroupDetails::class) state: NewGroupDetailsUiState,
) {
    PingogramPreview {
        NewGroupDetailsScreen(state = state, onEvent = {}, onNotAddedEvent = {}, onPickPhoto = {}, onBack = {})
    }
}
