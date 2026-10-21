package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.NewContactPreviewStates
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Add contact screen: hoists its [NewContactViewModel]. [onBack] leaves; [onAdded] leaves once the contact was
 * added. An invitation opens Android's share sheet ([shareInvite]); errors no field shows are snackbars.
 */
@Composable
internal fun NewContactRoute(
    onBack: () -> Unit,
    onAdded: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewContactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnAdded by rememberUpdatedState(onAdded)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewContactEffect.Added -> {
                    currentOnAdded(effect.userId)
                }

                is NewContactEffect.ShareInvite -> {
                    shareInvite(context, resources.getString(R.string.contacts_invite_text, effect.link))
                }

                // Snackbars queue up; later effects must not wait for them.
                is NewContactEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    NewContactScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Adding a contact (`REQUIREMENTS.md` FR-P1, `docs/DESIGN_SYSTEM.md` §6), pushed above the Contacts tab: a small top
 * bar with back and "Add contact" on `surfaceContainer`, and on the rounded `surface` sheet below a form at most
 * `contentMaxWidth` wide, inside the window margin:
 * - "Phone number" or "Username" as a connected button group;
 * - the first and last names, then the phone number (left to right, with the country its digits belong to below it)
 *   or the username after `@`, in Material's rounded tonal fields;
 * - the `FLOOD_WAIT` countdown ([FloodWaitNote]) or a [LinearWavyProgressIndicator] while Telegram is asked, then the
 *   default [Button] "Add" at the end, with the press morph.
 *
 * A number no one uses Telegram with crossfades the form into an invitation: a soft `MaterialShapes` badge, what is
 * the matter, "Change number" as a [TextButton] at the start and "Invite" at the end; back returns to the form. The
 * screen keeps clear of the keyboard, so the field being typed in stays in view.
 */
@Composable
internal fun NewContactScreen(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.notOnTelegram) { onEvent(NewContactEvent.ChangeNumber) }
    Scaffold(
        modifier = modifier,
        // The top bar's container; the form sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { ContactFormTopBar(stringResource(R.string.contacts_add), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            val motion = MaterialTheme.motionScheme
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = state.notOnTelegram,
                    modifier =
                        Modifier
                            .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.large)
                            .widthIn(max = MaterialTheme.sizes.contentMaxWidth),
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                            .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
                    },
                    label = "NewContactStep",
                ) { notOnTelegram ->
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                        if (notOnTelegram) NotOnTelegram(state, onEvent) else NewContactForm(state, onEvent)
                    }
                }
            }
        }
    }
}

/** The form: the method, the names, the phone number or the username, and "Add". */
@Composable
private fun NewContactForm(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
) {
    val submit = { onEvent(NewContactEvent.Submit) }
    val enabled = !state.isWorking
    ConnectedChoices(
        labels =
            listOf(
                stringResource(R.string.contacts_method_phone),
                stringResource(R.string.contacts_method_username),
            ),
        selectedIndex = state.method.ordinal,
        onSelect = { onEvent(NewContactEvent.MethodChanged(ContactMethod.entries[it])) },
        enabled = enabled,
    )
    NameFields(state, onEvent, enabled)
    if (state.method == ContactMethod.Username) {
        UsernameField(state, onEvent, enabled, submit)
    } else {
        PhoneNumberField(state, onEvent, enabled, submit)
    }
    FormEnd(
        floodWaitSeconds = state.floodWaitSeconds,
        floodWaitTotalSeconds = state.floodWaitTotalSeconds,
        isWorking = state.isWorking,
    ) {
        Button(onClick = submit, enabled = state.canSubmit, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.contacts_add_action), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The first name, optional with a username, and the last name, always optional. */
@Composable
private fun NameFields(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
    enabled: Boolean,
) {
    val optional = state.method == ContactMethod.Username
    ContactTextField(
        value = state.firstName,
        onValueChange = { onEvent(NewContactEvent.FirstNameChanged(it)) },
        spec =
            ContactFieldSpec(
                label =
                    stringResource(
                        if (optional) R.string.contacts_first_name_optional else R.string.contacts_first_name,
                    ),
                error = state.fieldError.textFor(ContactField.FirstName),
                enabled = enabled,
                keyboardOptions = nameKeyboard,
                autofill = ContentType.PersonFirstName,
            ),
    )
    ContactTextField(
        value = state.lastName,
        onValueChange = { onEvent(NewContactEvent.LastNameChanged(it)) },
        spec =
            ContactFieldSpec(
                label = stringResource(R.string.contacts_last_name_optional),
                enabled = enabled,
                keyboardOptions = nameKeyboard,
                autofill = ContentType.PersonLastName,
            ),
    )
}

/** The username after `@`, with a note on names; Done adds ([onSubmit]). */
@Composable
private fun UsernameField(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    ContactTextField(
        value = state.username,
        onValueChange = { onEvent(NewContactEvent.UsernameChanged(it)) },
        spec =
            ContactFieldSpec(
                label = stringResource(R.string.contacts_username),
                error = state.fieldError.textFor(ContactField.Username),
                supporting = stringResource(R.string.contacts_username_note),
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                onImeAction = onSubmit,
                prefix = "@",
            ),
    )
}

/** The phone number, left to right, with its country (or how to enter it) below; Done adds ([onSubmit]). */
@Composable
private fun PhoneNumberField(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    ContactTextField(
        value = state.phoneNumber,
        onValueChange = { onEvent(NewContactEvent.PhoneNumberChanged(it)) },
        spec =
            ContactFieldSpec(
                label = stringResource(R.string.contacts_phone_number),
                error = state.fieldError.textFor(ContactField.PhoneNumber),
                supporting = state.country ?: stringResource(R.string.contacts_phone_number_note),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                onImeAction = onSubmit,
                autofill = ContentType.PhoneNumber,
                textStyle = MaterialTheme.typography.bodyLarge.numericContent,
            ),
    )
}

/** The number is not on Telegram: a badge, what is the matter, "Change number" and "Invite". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.NotOnTelegram(
    state: NewContactUiState,
    onEvent: (NewContactEvent) -> Unit,
) {
    ContactBadge(
        shape = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_share,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(
        text =
            stringResource(R.string.contacts_not_on_telegram_title, state.phoneNumber.trim().bidiIsolated())
                .bidiIsolated(),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmallEmphasized,
    )
    Text(
        text = stringResource(R.string.contacts_not_on_telegram_body),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        TextButton(onClick = { onEvent(NewContactEvent.ChangeNumber) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.contacts_change_number), style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = { onEvent(NewContactEvent.Invite) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.contacts_invite_action), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/**
 * The end of a contact form: the `FLOOD_WAIT` countdown and the progress of a request, then the buttons ([actions]),
 * at the end, wrapping onto more lines when they do not fit side by side.
 */
@Composable
internal fun FormEnd(
    floodWaitSeconds: Long?,
    floodWaitTotalSeconds: Long,
    isWorking: Boolean,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.End,
    actions: @Composable () -> Unit,
) {
    floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = floodWaitTotalSeconds) }
    if (isWorking) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) { actions() }
}

/** Names start each word with a capital, and go on to the next field. */
internal val nameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

@PingogramPreviews
@Composable
private fun NewContactScreenPreview(
    @PreviewParameter(NewContactPreviewStates::class) state: NewContactUiState,
) {
    PingogramPreview { NewContactScreen(state = state, onEvent = {}, onBack = {}) }
}
