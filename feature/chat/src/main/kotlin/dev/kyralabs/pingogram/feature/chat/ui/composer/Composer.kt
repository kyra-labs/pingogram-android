package dev.kyralabs.pingogram.feature.chat.ui.composer

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordButton
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordDrag
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordingBar
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bottom of the conversation (`REQUIREMENTS.md` FR-C5, FR-C6, `docs/DESIGN_SYSTEM.md` §6): the [Composer] where
 * the user can write, below the bar of the message it replies to or edits ([ComposerBarRow]), which grows in and out on
 * the motion scheme's springs; a short centred label in `onSurfaceVariant` where they cannot; nothing in a channel
 * they only read. It is a `surfaceContainer` surface below the messages' sheet, which reaches under the keyboard and
 * the system bars, edge to edge, while its content stays above them.
 *
 * [onSend] gets what to send once the user asked for it (the edit, while editing); [onFocusChanged] whether the text
 * field has the focus; [focus] asks the field for the focus when the user starts a reply or an edit. The bar shows its
 * message through [onShowBarMessage] and ends the reply or edit through [onCloseBar]. The attach button, where the
 * chat takes attachments and the composer does not edit, opens the attach menu through [onAttach]. The microphone in
 * the send button's place records voice messages ([recording], the one in progress; what the user does goes to
 * [onVoiceEvent]).
 */
@Composable
internal fun ChatBottomBar(
    composer: ComposerUiState,
    text: ComposerText,
    onSend: (SendOptions) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Instant = Instant::now,
    focus: ComposerFocusState = rememberComposerFocusState(),
    onShowBarMessage: (messageId: Long) -> Unit = {},
    onCloseBar: () -> Unit = {},
    onAttach: () -> Unit = {},
    recording: VoiceRecordingUiState? = null,
    onVoiceEvent: (VoiceEvent) -> Unit = {},
) {
    // Nothing in a channel the user only reads, or while the chat is not known.
    if (composer == ComposerUiState.Hidden) return
    val drag = remember { RecordDrag() }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(BOTTOM_EDGES))
        when (composer) {
            is ComposerUiState.Unavailable -> {
                Text(
                    text = composer.reason.resolve(),
                    modifier =
                        insets
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.large),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is ComposerUiState.Available -> {
                Column(insets) {
                    BarArea(composer.bar, onShowBarMessage, onCloseBar)
                    Composer(
                        composer,
                        text,
                        onSend,
                        onFocusChanged,
                        focus,
                        onAttach,
                        VoiceInput(recording, onVoiceEvent, drag),
                        now,
                    )
                }
            }

            ComposerUiState.Hidden -> {}
        }
    }
}

/**
 * The bar of the message the composer replies to or edits, while there is one: it grows in from the top on the motion
 * scheme's spatial spring and fades in on its effects spring, and the last bar shown stays while it leaves.
 */
@Composable
private fun BarArea(
    bar: ComposerBar?,
    onShow: (messageId: Long) -> Unit,
    onClose: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(bar) }
    if (bar != null) shown = bar
    AnimatedVisibility(
        visible = bar != null,
        enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        shown?.let { ComposerBarRow(it, onShow, onClose) }
    }
}

/**
 * The composer: the attach button ([AttachButton]) where the chat takes attachments, a multi-line text field that grows
 * to [MAX_LINES] lines and then scrolls, and once there is something to send, the send split button ([SendButton])
 * beside it, which grows in on the motion scheme's spatial spring; while editing, a check ([SaveEditButton]) in its
 * place, also for an emptied caption, and no attach button. Formatting comes from the selection
 * menu ([formattingItems]) and Ctrl shortcuts, a link from [LinkDialog], a time from [ScheduleDialog]. Sending and
 * saving confirm with a haptic (`HapticFeedbackType.Confirm`). [focus] moves the focus to the text field. While the
 * field is empty the microphone ([RecordButton]) is in the send button's place; while it records, the recording bar
 * ([RecordingBar]) takes the place of the attach button and the field.
 */
@Suppress("LongParameterList") // Its state and text, where sends and the focus go, and the attach button.
@Composable
private fun Composer(
    composer: ComposerUiState.Available,
    text: ComposerText,
    onSend: (SendOptions) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focus: ComposerFocusState,
    onAttach: () -> Unit,
    voice: VoiceInput,
    now: () -> Instant = Instant::now,
) {
    val haptics = LocalHapticFeedback.current
    val edit = composer.bar as? ComposerBar.Edit
    val currentEdit by rememberUpdatedState(edit)
    val send = { options: SendOptions ->
        if (!text.isBlank || currentEdit?.canBeEmpty == true) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSend(options)
        }
    }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    val canSend by remember(text) { derivedStateOf { !text.isBlank } }
    val focusRequester = rememberFocusRequester(focus)
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        val recording = voice.recording
        if (recording != null) {
            RecordingBar(recording, voice.drag, voice.onEvent, Modifier.weight(1f))
        } else {
            Appearing(visible = composer.attachOptions.isNotEmpty()) { AttachButton(onAttach) }
            ComposerField(
                text = text,
                onSend = { send(SendOptions()) },
                onLink = { linking = it },
                onFocusChanged = onFocusChanged,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }
        ComposerAction(edit, canSend, composer.canSchedule, send, onSchedule = { scheduling = true }, voice)
    }
    linking?.let { selection ->
        LinkDialog(
            currentUrl = text.linkAt(selection),
            onDismiss = { linking = null },
            onConfirm = { url ->
                text.setLink(url, selection)
                linking = null
            },
        )
    }
    if (scheduling) {
        ScheduleDialog(
            now = now,
            onDismiss = { scheduling = false },
            onSchedule = { at ->
                scheduling = false
                send(SendOptions(scheduledAt = at.epochSecond))
            },
        )
    }
}

/**
 * The button beside the text field: the check that saves an [edit] while there is something to save (also for an
 * emptied caption); else the send button while there is something to send ([canSend]), whose menu schedules where
 * [canSchedule] ([onSchedule]), and the microphone ([RecordButton]) while there is not. While a voice message records,
 * the record button stays, also when an edit starts meanwhile.
 */
@Suppress("LongParameterList") // What to save or send and how, and the voice recording.
@Composable
private fun RowScope.ComposerAction(
    edit: ComposerBar.Edit?,
    canSend: Boolean,
    canSchedule: Boolean,
    send: (SendOptions) -> Unit,
    onSchedule: () -> Unit,
    voice: VoiceInput,
) {
    val recording = voice.recording
    if (edit != null) {
        // An edit started while a recording goes on hands-free waits until the recording is sent or deleted.
        Appearing(visible = recording == null && (canSend || edit.canBeEmpty)) {
            SaveEditButton(onSave = { send(SendOptions()) })
        }
        Appearing(visible = recording != null) { RecordButton(recording, voice.drag, voice.onEvent) }
    } else {
        Appearing(visible = canSend && recording == null) {
            SendButton(
                canSchedule = canSchedule,
                onSend = { send(SendOptions()) },
                onSendSilently = { send(SendOptions(silent = true)) },
                onSchedule = onSchedule,
            )
        }
        Appearing(visible = !canSend || recording != null) { RecordButton(recording, voice.drag, voice.onEvent) }
    }
}

/**
 * The voice recording in progress ([recording], null while none), where what the user does with it goes, and where the
 * finger holding the microphone is ([drag]), which the button and the recording bar share.
 */
private data class VoiceInput(
    val recording: VoiceRecordingUiState?,
    val onEvent: (VoiceEvent) -> Unit,
    val drag: RecordDrag,
)

/**
 * Shows [content] while [visible]: it grows in from nothing on the motion scheme's fast spatial spring, and fades in on
 * its effects spring; the text field beside it makes room.
 */
@Composable
private fun RowScope.Appearing(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter =
            expandHorizontally(motionScheme.fastSpatialSpec()) +
                scaleIn(motionScheme.fastSpatialSpec()) +
                fadeIn(motionScheme.fastEffectsSpec()),
        exit =
            shrinkHorizontally(motionScheme.fastSpatialSpec()) +
                scaleOut(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
    ) { content() }
}

/**
 * The text field: outlined, `extraLarge` corners, `bodyLarge`, [placeholder] ("Message") while empty. Enter on a
 * hardware keyboard sends ([onSend]) and Shift+Enter starts a new line; the on-screen keyboard's Enter always starts
 * one. The selection menu, Ctrl+B/I/U/K and screen readers' actions format the selected text; "Link" asks for an
 * address ([onLink]). The attach preview's caption is one too.
 */
@Composable
internal fun ComposerField(
    text: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.composer_placeholder),
) {
    val labels = rememberFormattingLabels()
    val hasSelection by remember(text) { derivedStateOf { !text.state.selection.collapsed } }
    // onFocusChanged also reports the first state, unfocused: only changes are news.
    val focus = remember { FocusReport() }
    OutlinedTextField(
        state = text.state,
        modifier =
            modifier
                .onFocusChanged { state ->
                    if (state.isFocused != focus.isFocused) {
                        focus.isFocused = state.isFocused
                        onFocusChanged(state.isFocused)
                    }
                }.onPreviewKeyEvent { event ->
                    when {
                        event.isSendKey() -> {
                            onSend()
                            true
                        }

                        else -> {
                            formattingShortcut(event, text, onLink)
                        }
                    }
                }.appendTextContextMenuComponents { formattingItems(text, labels, onLink) }
                .semantics { if (hasSelection) customActions = formattingActions(text, labels, onLink) },
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder) },
        inputTransformation = text.inputTransformation,
        outputTransformation = rememberFormattingTransformation(text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_LINES),
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Whether the text field had the focus when it last reported it; read in callbacks only, so not snapshot state. */
private class FocusReport(
    var isFocused: Boolean = false,
)

/**
 * Enter pressed on a hardware keyboard without a modifier. The on-screen keyboard sends its Enter as a key event too,
 * flagged as its own, and that one keeps starting a new line.
 */
private fun KeyEvent.isSendKey(): Boolean =
    type == KeyEventType.KeyDown &&
        (key == Key.Enter || key == Key.NumPadEnter) &&
        !isShiftPressed &&
        !isCtrlPressed &&
        !isAltPressed &&
        !isMetaPressed &&
        nativeKeyEvent.flags and FLAG_SOFT_KEYBOARD == 0

/** The edges the bottom bar keeps clear of: the keyboard and the navigation bar below, cutouts at the sides. */
private val BOTTOM_EDGES = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

/** The lines the text field grows to before it scrolls. */
private const val MAX_LINES = 6

@PingogramPreviews
@Composable
private fun ComposerEmptyPreview() {
    PingogramPreview {
        ChatBottomBar(ComposerUiState.Available(canSchedule = true), remember { ComposerText() }, {}, {})
    }
}

@PingogramPreviews
@Composable
private fun ComposerFormattedPreview() {
    PingogramPreview {
        ChatBottomBar(ComposerUiState.Available(canSchedule = true), remember { ComposerText(PREVIEW_TEXT) }, {}, {})
    }
}

@PingogramPreviews
@Composable
private fun ComposerUnavailablePreview() {
    PingogramPreview {
        ChatBottomBar(
            ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted)),
            ComposerText(),
            {},
            {},
        )
    }
}

private val PREVIEW_TEXT =
    FormattedText(
        "Bold, italic and a link",
        persistentListOf(
            TextEntity(0, 4, TextEntityType.Bold),
            TextEntity(6, 6, TextEntityType.Italic),
            TextEntity(19, 4, TextEntityType.TextUrl("https://example.org")),
        ),
    )
