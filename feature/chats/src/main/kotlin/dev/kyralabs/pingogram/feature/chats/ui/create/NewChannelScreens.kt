package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.CreateChatPreviewStates
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The first step of creating a channel (`REQUIREMENTS.md` FR-P4, `docs/DESIGN_SYSTEM.md` §6, "New channel"): a small
 * top bar with back ([onBack]) and "New channel", and on the sheet a form at most `contentMaxWidth` wide: the photo
 * ([ChatPhotoButton], the system photo picker through [onPickPhoto]), the name (required) and the description in
 * Material's rounded tonal fields, then the `FLOOD_WAIT` countdown or a wavy progress indicator while the channel is
 * created, and the default [Button] "Next" at the end, which creates it.
 */
@Composable
internal fun NewChannelDetailsScreen(
    state: NewChannelDetailsUiState,
    onEvent: (ChatDetailsEvent) -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val enabled = !state.isCreating
    CreateChatScaffold(
        title = stringResource(R.string.new_chat_channel),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { contentPadding ->
        CreateChatForm(contentPadding) {
            ChatPhotoButton(
                photo = state.photo,
                description = stringResource(R.string.create_channel_photo),
                onPick = onPickPhoto,
                onRemove = { onEvent(ChatDetailsEvent.RemovePhoto) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            CreateChatTextField(
                value = state.title,
                onValueChange = { onEvent(ChatDetailsEvent.TitleChanged(it)) },
                label = stringResource(R.string.create_channel_name),
                error = stringResource(R.string.create_channel_name_error).takeIf { state.titleError },
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
            )
            CreateChatTextField(
                value = state.description,
                onValueChange = { onEvent(ChatDetailsEvent.DescriptionChanged(it)) },
                label = stringResource(R.string.create_channel_description),
                supporting = stringResource(R.string.create_channel_description_note),
                enabled = enabled,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
            )
            FormEnd(floodWait = state.floodWait, isWorking = state.isCreating) {
                Button(
                    onClick = { onEvent(ChatDetailsEvent.Create) },
                    enabled = state.canCreate,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.create_next), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/**
 * The second step of creating a channel, which exists by then (`docs/DESIGN_SYSTEM.md` §6, "New channel"): a small top
 * bar with "Channel type" and no back button (system back opens the channel), and on the sheet private or public as a
 * connected button group with what each means, crossfading on the effects springs between
 * - **private**: the invite link on a tonal card in `surfaceContainerHigh` with "Copy" ([onCopyLink]) and "Share"
 *   ([onShareLink]), the delayed loading indicator while it comes, or its error with "Try again";
 * - **public**: the username after `t.me/` in Material's rounded tonal field, with what is known about it below
 *   ([usernameText]): its first problem, "Checking…", available, or Telegram's refusal as the field's error;
 *
 * then the `FLOOD_WAIT` countdown or a wavy progress indicator while the username is set, and the default [Button]
 * "Next" at the end.
 */
@Suppress("LongParameterList") // The step's state and events, and the link's two actions.
@Composable
internal fun NewChannelTypeScreen(
    state: NewChannelTypeUiState,
    onEvent: (NewChannelTypeEvent) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val motion = MaterialTheme.motionScheme
    val enabled = !state.isSaving
    CreateChatScaffold(
        title = stringResource(R.string.create_channel_type),
        onBack = null,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { contentPadding ->
        CreateChatForm(contentPadding) {
            ConnectedChoices(
                labels = listOf(stringResource(R.string.create_private), stringResource(R.string.create_public)),
                selectedIndex = if (state.isPublic) 1 else 0,
                onSelect = { onEvent(NewChannelTypeEvent.TypeChanged(isPublic = it == 1)) },
                enabled = enabled,
            )
            AnimatedContent(
                targetState = state.isPublic,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                        .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
                },
                label = "ChannelType",
            ) { isPublic ->
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    Text(
                        text =
                            stringResource(if (isPublic) R.string.create_public_note else R.string.create_private_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isPublic) {
                        UsernameField(state, onEvent, enabled)
                    } else {
                        InviteLink(state.inviteLink, onEvent, onCopyLink, onShareLink)
                    }
                }
            }
            FormEnd(floodWait = state.floodWait, isWorking = state.isSaving) {
                Button(
                    onClick = { onEvent(NewChannelTypeEvent.Continue) },
                    enabled = state.canContinue,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.create_next), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/** The public link's username after `t.me/`, left to right, with what is known about it below. */
@Composable
private fun UsernameField(
    state: NewChannelTypeUiState,
    onEvent: (NewChannelTypeEvent) -> Unit,
    enabled: Boolean,
) {
    val (text, isError) = usernameText(state.usernameStatus, state.username)
    CreateChatTextField(
        value = state.username,
        onValueChange = { onEvent(NewChannelTypeEvent.UsernameChanged(it)) },
        label = stringResource(R.string.create_link),
        error = text.takeIf { isError },
        supporting = text.takeIf { !isError },
        enabled = enabled,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        onImeAction = { onEvent(NewChannelTypeEvent.Continue) },
        prefix = stringResource(R.string.create_link_prefix),
        textStyle = MaterialTheme.typography.bodyLarge.numericContent,
    )
}

/** What to say below the username for [status], and whether it is an error. */
@Composable
private fun usernameText(
    status: UsernameStatus,
    username: String,
): Pair<String, Boolean> =
    when (status) {
        UsernameStatus.Empty -> {
            stringResource(R.string.create_link_note) to false
        }

        UsernameStatus.Checking -> {
            stringResource(R.string.create_link_checking) to false
        }

        UsernameStatus.CheckFailed -> {
            stringResource(R.string.create_link_check_failed) to false
        }

        is UsernameStatus.Problem -> {
            val text =
                when (status.problem) {
                    UsernameProblem.TooShort -> {
                        val min = ProfileLimits.USERNAME_LENGTH_MIN
                        pluralStringResource(R.plurals.create_link_too_short, min, min)
                    }

                    UsernameProblem.TooLong -> {
                        val max = ProfileLimits.USERNAME_LENGTH_MAX
                        pluralStringResource(R.plurals.create_link_too_long, max, max)
                    }

                    UsernameProblem.InvalidCharacter -> {
                        stringResource(R.string.create_link_characters)
                    }

                    UsernameProblem.StartsWithNonLetter -> {
                        stringResource(R.string.create_link_first_letter)
                    }

                    UsernameProblem.EndsWithUnderscore -> {
                        stringResource(R.string.create_link_ends_with_underscore)
                    }

                    UsernameProblem.ConsecutiveUnderscores -> {
                        stringResource(R.string.create_link_consecutive_underscores)
                    }
                }
            text to true
        }

        is UsernameStatus.Checked -> {
            when (status.check) {
                UsernameCheck.Available -> stringResource(R.string.create_link_available, username) to false
                UsernameCheck.Invalid -> stringResource(R.string.create_link_invalid) to true
                UsernameCheck.Taken -> stringResource(R.string.create_link_taken) to true
                UsernameCheck.ForSale -> stringResource(R.string.create_link_for_sale) to true
                UsernameCheck.Unavailable -> stringResource(R.string.create_link_unavailable) to true
            }
        }
    }

/** The private channel's invite link with "Copy" and "Share", or what keeps it from showing. */
@Composable
private fun InviteLink(
    link: InviteLinkState,
    onEvent: (NewChannelTypeEvent) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            when (link) {
                InviteLinkState.Loading -> {
                    val description = stringResource(R.string.create_link_loading)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                is InviteLinkState.Error -> {
                    Text(
                        text = errorText(link.error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { onEvent(NewChannelTypeEvent.RetryLink) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
                    }
                }

                is InviteLinkState.Ready -> {
                    Text(
                        text = link.link,
                        style = MaterialTheme.typography.bodyLarge.numericContent,
                    )
                    LinkActions(link.link, onCopyLink, onShareLink)
                }
            }
        }
    }
}

@Composable
private fun LinkActions(
    link: String,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        FilledTonalButton(onClick = { onCopyLink(link) }, shapes = ButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_content_copy),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.create_link_copy), style = MaterialTheme.typography.labelLarge)
        }
        FilledTonalButton(onClick = { onShareLink(link) }, shapes = ButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_share),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.create_link_share), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@PingogramPreviews
@Composable
private fun NewChannelDetailsScreenPreview(
    @PreviewParameter(CreateChatPreviewStates.ChannelDetails::class) state: NewChannelDetailsUiState,
) {
    PingogramPreview { NewChannelDetailsScreen(state = state, onEvent = {}, onPickPhoto = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun NewChannelTypeScreenPreview(
    @PreviewParameter(CreateChatPreviewStates.ChannelType::class) state: NewChannelTypeUiState,
) {
    PingogramPreview { NewChannelTypeScreen(state = state, onEvent = {}, onCopyLink = {}, onShareLink = {}) }
}
