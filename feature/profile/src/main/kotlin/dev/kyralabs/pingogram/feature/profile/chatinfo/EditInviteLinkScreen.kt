package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.InviteLinkSettings
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The page that creates or edits an invite link: hoists its view model; closes through [onSaved] once saved. */
@Composable
internal fun EditInviteLinkRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditInviteLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val haptics = LocalHapticFeedback.current
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditInviteLinkEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnSaved()
                }

                is EditInviteLinkEffect.ShowError -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails))
                }
            }
        }
    }
    EditInviteLinkScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Creating or editing an invite link (`docs/DESIGN_SYSTEM.md` §6 "Invite links"), a settings-style page: the name in a
 * rounded tonal `TextField` with its count, "Expires" and "Limit members" as groups of radio rows (single choices),
 * "Request admin approval" as a switch row, which turns the member limit off, then a `LinearWavyProgressIndicator`
 * while Telegram answers and a default `Button` "Create link" or "Save" at the end.
 */
@Composable
internal fun EditInviteLinkScreen(
    state: EditInviteLinkUiState,
    onEvent: (EditInviteLinkEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val isNew = (state as? EditInviteLinkUiState.Content)?.isNew != false
    InfoPageScaffold(
        title = stringResource(if (isNew) R.string.chat_info_link_new_title else R.string.chat_info_link_edit_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        when (state) {
            EditInviteLinkUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is EditInviteLinkUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(EditInviteLinkEvent.Retry) } }
            }

            is EditInviteLinkUiState.Content -> {
                InfoPageContent(innerPadding) { LinkForm(state, onEvent) }
            }
        }
    }
}

@Composable
private fun LinkForm(
    state: EditInviteLinkUiState.Content,
    onEvent: (EditInviteLinkEvent) -> Unit,
) {
    InfoTextField(
        value = state.name,
        onValueChange = { onEvent(EditInviteLinkEvent.NameChanged(it)) },
        label = stringResource(R.string.chat_info_link_name),
        enabled = !state.isSaving,
        supporting =
            stringResource(R.string.chat_info_link_name_note, state.name.length, InviteLinkSettings.NAME_LENGTH_MAX),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        modifier = Modifier.testTag(LINK_NAME_TEST_TAG),
    )
    ChoiceGroup(
        title = stringResource(R.string.chat_info_link_expires_header),
        choices = state.expiryChoices,
        selected = state.expiry,
        enabled = !state.isSaving,
        label = { expiryLabel(it) },
        onSelect = { onEvent(EditInviteLinkEvent.SelectExpiry(it)) },
    )
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    ChoiceGroup(
        title = stringResource(R.string.chat_info_link_limit_header),
        choices = state.limitChoices,
        selected = state.memberLimit,
        enabled = !state.isSaving && !state.createsJoinRequest,
        label = { limit ->
            if (limit == 0) {
                stringResource(R.string.chat_info_link_no_limit)
            } else {
                pluralStringResource(R.plurals.chat_info_link_limit, limit, numbers.format(limit))
            }
        },
        onSelect = { onEvent(EditInviteLinkEvent.SelectLimit(it)) },
    )
    ProfileGroup(
        listOf(
            ProfileRow("requests") { shapes ->
                SwitchRow(
                    label = stringResource(R.string.chat_info_link_requests_switch),
                    checked = state.createsJoinRequest,
                    onCheckedChange = { onEvent(EditInviteLinkEvent.SetJoinRequests(it)) },
                    shapes = shapes,
                    enabled = !state.isSaving,
                    supporting = stringResource(R.string.chat_info_link_requests_summary),
                    modifier = Modifier.testTag(JOIN_REQUESTS_TEST_TAG),
                )
            },
        ),
    )
    SaveRow(state.isNew, state.isSaving) { onEvent(EditInviteLinkEvent.Save) }
}

/** A wavy line while Telegram answers, then "Create link" ([isNew]) or "Save" at the end. */
@Composable
private fun SaveRow(
    isNew: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        if (isSaving) SavingIndicator()
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, shapes = ButtonDefaults.shapes(), enabled = !isSaving) {
                Text(
                    stringResource(if (isNew) R.string.chat_info_link_create_button else R.string.chat_info_save),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** A single choice: [choices] as radio rows of one segmented group under [title], [selected]'s row selected. */
@Suppress("LongParameterList") // What the group shows and does.
@Composable
private fun <T> ChoiceGroup(
    title: String,
    choices: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rows =
        choices.map { choice ->
            ProfileRow(choice.toString()) { shapes ->
                RadioRow(
                    label = label(choice),
                    selected = choice == selected,
                    enabled = enabled,
                    shapes = shapes,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(choice)
                    },
                )
            }
        }
    ProfileGroup(rows, Modifier.selectableGroup(), title = title)
}

/** One choice: the selectable segmented list item with a leading `RadioButton`, which screen readers hear as one. */
@Composable
internal fun RadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = profileRowColors(),
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    ) {
        Text(label)
    }
}

/** "Never", "In 1 hour", "In 1 day", "In 1 week", or an edited link's own expiry ("On Oct 30, 2026"). */
@Composable
private fun expiryLabel(choice: ExpiryChoice): String =
    when (choice) {
        ExpiryChoice.Never -> {
            stringResource(R.string.chat_info_link_never)
        }

        is ExpiryChoice.After -> {
            when (choice.seconds) {
                HOUR -> stringResource(R.string.chat_info_link_in_hour)
                DAY -> stringResource(R.string.chat_info_link_in_day)
                else -> stringResource(R.string.chat_info_link_in_week)
            }
        }

        is ExpiryChoice.At -> {
            val locale = LocalConfiguration.current.locales[0]
            val date =
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(Instant.ofEpochSecond(choice.epochSeconds).atZone(ZoneId.systemDefault()))
            stringResource(R.string.chat_info_link_on, date)
        }
    }

/** The test tag of the name field, for tests that type into it. */
internal const val LINK_NAME_TEST_TAG = "invite_link_name"

/** The test tag of the join requests switch. */
internal const val JOIN_REQUESTS_TEST_TAG = "invite_link_requests"

private const val HOUR = 3_600L
private const val DAY = 86_400L
