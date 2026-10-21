package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.domain.model.ProfileLimits
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.domain.model.characterCount
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

// The fields of the Edit profile page, rounded tonal fields directly on the page under their headers, below the photo
// (ProfilePhotoEditor).

/** The first name, required, and the last name, each in a row and counting its characters up to Telegram's 64. */
@Composable
internal fun NameSection(
    state: EditProfileUiState.Content,
    onEvent: (EditProfileEvent) -> Unit,
) {
    FieldGroup(title = stringResource(R.string.settings_section_profile_name)) {
        ProfileTextField(
            value = state.fields.firstName,
            onValueChange = { onEvent(EditProfileEvent.FirstNameChanged(it)) },
            label = stringResource(R.string.settings_profile_first_name),
            enabled = !state.isSaving,
            message = state.firstNameProblem?.let { fieldProblemMessage(it, ProfileLimits.NAME_LENGTH_MAX) },
            counter = counter(state.fields.firstName, ProfileLimits.NAME_LENGTH_MAX),
            keyboardOptions = nameKeyboard,
            contentType = ContentType.PersonFirstName,
        )
        ProfileTextField(
            value = state.fields.lastName,
            onValueChange = { onEvent(EditProfileEvent.LastNameChanged(it)) },
            label = stringResource(R.string.settings_profile_last_name),
            enabled = !state.isSaving,
            message = state.lastNameProblem?.let { fieldProblemMessage(it, ProfileLimits.NAME_LENGTH_MAX) },
            counter = counter(state.fields.lastName, ProfileLimits.NAME_LENGTH_MAX),
            keyboardOptions = nameKeyboard,
            contentType = ContentType.PersonLastName,
        )
    }
}

/**
 * The username after an `@` prefix, with what is known about it below ([usernameMessage]): available in `primary`,
 * anything that stops it in `error`, announced to screen readers as it changes. A note says what a username is for.
 */
@Composable
internal fun UsernameSection(
    state: EditProfileUiState.Content,
    onEvent: (EditProfileEvent) -> Unit,
) {
    FieldGroup(
        title = stringResource(R.string.settings_section_profile_username),
        footer = stringResource(R.string.settings_profile_username_note),
    ) {
        ProfileTextField(
            value = state.fields.username,
            onValueChange = { onEvent(EditProfileEvent.UsernameChanged(it)) },
            label = stringResource(R.string.settings_profile_username),
            enabled = !state.isSaving,
            message = usernameMessage(state.usernameStatus, state.fields.username),
            prefix = "@",
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            announcesMessage = true,
        )
    }
}

/**
 * The bio: several lines, counting its characters up to TDLib's `bio_length_max`. Telegram keeps a bio to one
 * paragraph, so the keyboard offers Done rather than a new line.
 */
@Composable
internal fun BioSection(
    state: EditProfileUiState.Content,
    onEvent: (EditProfileEvent) -> Unit,
) {
    FieldGroup(
        title = stringResource(R.string.settings_section_profile_bio),
        footer = stringResource(R.string.settings_profile_bio_note),
    ) {
        ProfileTextField(
            value = state.fields.bio,
            onValueChange = { onEvent(EditProfileEvent.BioChanged(it)) },
            label = stringResource(R.string.settings_profile_bio),
            enabled = !state.isSaving,
            message = state.bioProblem?.let { fieldProblemMessage(it, state.bioLengthMax) },
            counter = counter(state.fields.bio, state.bioLengthMax),
            singleLine = false,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
    }
}

/**
 * Fields under their [title] (a [SettingsSectionHeader]) with a [footer] note below them, directly on the page, without
 * a segment around them (`docs/DESIGN_SYSTEM.md` §6, "Text fields": one rounded tonal style, never a box in a box),
 * `spacing.small` apart beside their supporting text.
 */
@Composable
private fun FieldGroup(
    title: String,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SettingsSectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), content = content)
        if (footer != null) SettingsFooter(footer)
    }
}

/** What a field says below it: [text], in the error role if [isError], else in [color] (the field's own if unset). */
@Immutable
internal data class FieldMessage(
    val text: String,
    val isError: Boolean,
    val color: Color = Color.Unspecified,
)

/**
 * An M3 [TextField] in the rounded tonal style (`TextFieldDefaults.roundedShape`, `tonalColors()`), as the composer's
 * and 2-step verification's, as wide as the page, with [message] and [counter] below it. An error message is reported
 * as the field's error to screen readers; with [announcesMessage], any message is announced as it changes. What is
 * typed takes its own direction ([userContent]): a Latin name stays left to right in a right-to-left layout.
 */
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    message: FieldMessage? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    counter: FieldCounter? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    contentType: ContentType? = null,
    announcesMessage: Boolean = false,
) {
    val isError = message?.isError == true
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    if (isError) error(message.text)
                    if (contentType != null) this.contentType = contentType
                },
        enabled = enabled,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        supportingText =
            if (message != null || counter != null) {
                { SupportingText(message, counter, announcesMessage) }
            } else {
                null
            },
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else BIO_MIN_LINES,
        textStyle = LocalTextStyle.current.userContent,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** [message] at the start and [counter] at the end of a field's supporting text. */
@Composable
private fun SupportingText(
    message: FieldMessage?,
    counter: FieldCounter?,
    announcesMessage: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Text(
            text = message?.text.orEmpty(),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { if (announcesMessage) liveRegion = LiveRegionMode.Polite },
            color = message?.color ?: Color.Unspecified,
        )
        if (counter != null) {
            Text(text = counter.text, modifier = Modifier.semantics { contentDescription = counter.description })
        }
    }
}

/** A field's character counter: [text] as it shows ("12/64"), and its [description] for screen readers. */
@Immutable
private data class FieldCounter(
    val text: String,
    val description: String,
)

/** How many characters [text] has of [max], as TDLib counts them. */
@Composable
private fun counter(
    text: String,
    max: Int,
): FieldCounter {
    val count = text.characterCount()
    return FieldCounter(
        text = stringResource(R.string.settings_profile_counter, count, max),
        description = pluralStringResource(R.plurals.settings_profile_counter_description, max, count, max),
    )
}

@Composable
private fun fieldProblemMessage(
    problem: FieldProblem,
    max: Int,
): FieldMessage =
    FieldMessage(
        text =
            when (problem) {
                FieldProblem.Required -> stringResource(R.string.settings_profile_first_name_required)
                FieldProblem.TooLong -> pluralStringResource(R.plurals.settings_profile_too_long, max, max)
            },
        isError = true,
    )

/** What the field says about [username] in [status]; nothing for the username TDLib has. */
@Composable
private fun usernameMessage(
    status: UsernameStatus,
    username: String,
): FieldMessage? =
    when (status) {
        UsernameStatus.Unchanged -> {
            null
        }

        UsernameStatus.Removed -> {
            FieldMessage(stringResource(R.string.settings_profile_username_removed), isError = false)
        }

        UsernameStatus.Checking -> {
            FieldMessage(stringResource(R.string.settings_profile_username_checking), isError = false)
        }

        UsernameStatus.CheckFailed -> {
            FieldMessage(stringResource(R.string.settings_profile_username_check_failed), isError = false)
        }

        is UsernameStatus.Problem -> {
            FieldMessage(usernameProblemText(status.problem), isError = true)
        }

        is UsernameStatus.Checked -> {
            when (status.result) {
                UsernameCheck.Available -> {
                    FieldMessage(
                        text = stringResource(R.string.settings_profile_username_available, username),
                        isError = false,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                UsernameCheck.Taken -> {
                    FieldMessage(stringResource(R.string.settings_profile_username_taken), isError = true)
                }

                UsernameCheck.ForSale -> {
                    FieldMessage(stringResource(R.string.settings_profile_username_for_sale), isError = true)
                }

                UsernameCheck.Invalid -> {
                    FieldMessage(stringResource(R.string.settings_profile_username_invalid), isError = true)
                }

                UsernameCheck.Unavailable -> {
                    FieldMessage(stringResource(R.string.settings_profile_username_unavailable), isError = true)
                }
            }
        }
    }

@Composable
private fun usernameProblemText(problem: UsernameProblem): String =
    when (problem) {
        UsernameProblem.TooShort -> {
            pluralStringResource(
                R.plurals.settings_profile_username_too_short,
                ProfileLimits.USERNAME_LENGTH_MIN,
                ProfileLimits.USERNAME_LENGTH_MIN,
            )
        }

        UsernameProblem.TooLong -> {
            pluralStringResource(
                R.plurals.settings_profile_username_too_long,
                ProfileLimits.USERNAME_LENGTH_MAX,
                ProfileLimits.USERNAME_LENGTH_MAX,
            )
        }

        UsernameProblem.InvalidCharacter -> {
            stringResource(R.string.settings_profile_username_invalid_character)
        }

        UsernameProblem.StartsWithNonLetter -> {
            stringResource(R.string.settings_profile_username_starts_with_non_letter)
        }

        UsernameProblem.EndsWithUnderscore -> {
            stringResource(R.string.settings_profile_username_ends_with_underscore)
        }

        UsernameProblem.ConsecutiveUnderscores -> {
            stringResource(R.string.settings_profile_username_consecutive_underscores)
        }
    }

private val nameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

/** The bio field's height while it is short: three lines, so it reads as a field for a few sentences. */
private const val BIO_MIN_LINES = 3
