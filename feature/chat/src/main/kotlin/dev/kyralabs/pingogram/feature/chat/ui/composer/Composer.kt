package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanel
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsButton
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordButton
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordDrag
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordingBar
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * The bottom of the conversation (`REQUIREMENTS.md` FR-C5, FR-C6, `docs/DESIGN_SYSTEM.md` §6): the [Composer] where
 * the user can write, below the bar of the message it replies to or edits ([ComposerBarRow]), which grows in and out on
 * the motion scheme's springs; a short centred label in `onSurfaceVariant` where they cannot; nothing in a channel
 * they only read. It is a `surface` below the messages' sheet, of the sheet's color as in Google Messages, which
 * reaches under the keyboard and the system bars, edge to edge, while its content stays above them; on wide windows
 * the composer and its bar are no wider than a bubble can be (`sizes.bubbleMaxWidth`), centred.
 *
 * [onSend] gets what to send once the user asked for it (the edit, while editing); [onFocusChanged] whether the text
 * field has the focus; [focus] asks the field for the focus when the user starts a reply or an edit. The bar shows its
 * message through [onShowBarMessage] and ends the reply or edit through [onCloseBar]. The attach button, where the
 * chat takes attachments and the composer does not edit, opens the attach menu through [onAttach], and the gallery
 * button at the field's end, while it is empty, the system photo picker through [onGallery]. The microphone in
 * the send button's place records voice messages ([recording], the one in progress; what the user does goes to
 * [onVoiceEvent]). Where the user can choose who they send as, that sender's avatar comes first and opens the send-as
 * picker through [onSendAs]. The smiley at the end of the field opens the emoji and sticker panel ([EmojiPanel],
 * showing [panel]) in the keyboard's place ([PanelArea], [panelState]): its emoji go into the text at the cursor, its
 * stickers are sent through [onSendSticker], and what it loads goes to [onPanelEvent]. In a public group or channel
 * the user is not in, a filled "Join" button takes the composer's place ([JoinButton], [onJoin]).
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
    onGallery: () -> Unit = {},
    recording: VoiceRecordingUiState? = null,
    onVoiceEvent: (VoiceEvent) -> Unit = {},
    onSendAs: () -> Unit = {},
    panel: EmojiPanelUiState = EmojiPanelUiState(),
    onPanelEvent: (EmojiPanelEvent) -> Unit = {},
    onSendSticker: (Sticker) -> Unit = {},
    panelState: ComposerPanelState = rememberComposerPanelState(),
    onJoin: () -> Unit = {},
) {
    // Nothing in a channel the user only reads, or while the chat is not known.
    if (composer == ComposerUiState.Hidden) return
    val drag = remember { RecordDrag() }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        when (composer) {
            is ComposerUiState.Unavailable -> {
                UnavailableLabel(composer.reason)
            }

            is ComposerUiState.Join -> {
                JoinButton(composer, onJoin)
            }

            is ComposerUiState.Available -> {
                ComposerPanelEffects(panelState, enabled = recording == null)
                if (panelState.isOpen) LaunchedEffect(Unit) { onPanelEvent(EmojiPanelEvent.Opened) }
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    // As wide as a bubble can be, centred, so the field never stretches across a wide window.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentWidth()
                            .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth),
                    ) {
                        BarArea(composer.bar, onShowBarMessage, onCloseBar)
                        Composer(
                            composer,
                            text,
                            onSend,
                            onFocusChanged,
                            focus,
                            AttachInput(onAttach, onGallery),
                            VoiceInput(recording, onVoiceEvent, drag),
                            panelState,
                            now,
                            onSendAs,
                        )
                    }
                    PanelArea(panelState) {
                        EmojiPanel(
                            state = panel,
                            onEmoji = { emoji ->
                                text.insert(emoji)
                                onPanelEvent(EmojiPanelEvent.EmojiPicked(emoji))
                            },
                            onBackspace = text::deleteBackward,
                            onSticker = onSendSticker,
                            onEvent = onPanelEvent,
                            modifier = Modifier.fillMaxSize(),
                            // Not while editing: a sticker would be a new message.
                            showStickers = composer.attachOptions.isNotEmpty(),
                        )
                    }
                }
            }

            ComposerUiState.Hidden -> {}
        }
    }
}

/** Why the user cannot write here ([reason]), centred in `onSurfaceVariant`, above the keyboard and system bars. */
@Composable
private fun UnavailableLabel(reason: UiText) {
    Text(
        text = reason.resolve(),
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(BOTTOM_EDGES))
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
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
 * The composer, as Google Messages' (`docs/DESIGN_SYSTEM.md` §6 Composer): a 56 dp pill ([ComposerField], a
 * multi-line text field that grows to six lines and then scrolls) 8 dp from the window's sides, beside a 56 dp round
 * button, the two bottom-aligned. In the pill, the attach "+" ([AttachButton]) at the start where the chat takes
 * attachments, and the emoji button ([PanelButton]) and, while the field is empty, the gallery ([GalleryButton]) at the
 * end. The round button is the microphone ([RecordButton]) while the field is empty and, once there is something to
 * send, the send button ([SendButton], a long press for its other ways to send), growing in on the motion scheme's
 * spatial spring; while editing, a check ([SaveEditButton]) in its place, also for an emptied caption, and no attach or
 * gallery button. Formatting comes from the selection menu ([formattingItems]) and Ctrl shortcuts, a link from
 * [LinkDialog], a time from [ScheduleDialog]. Sending and saving confirm with a haptic (`HapticFeedbackType.Confirm`).
 * [focus] moves the focus to the text field. While it records, the recording bar ([RecordingBar]) takes the place of
 * the pill. Before the pill, the send-as avatar ([SendAsButton]) where the user can choose who they send as
 * ([onSendAs]).
 */
@Suppress("LongParameterList") // Its state and text, where sends and the focus go, and the attach buttons.
@Composable
private fun Composer(
    composer: ComposerUiState.Available,
    text: ComposerText,
    onSend: (SendOptions) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focus: ComposerFocusState,
    attach: AttachInput,
    voice: VoiceInput,
    panel: ComposerPanelState,
    now: () -> Instant = Instant::now,
    onSendAs: () -> Unit = {},
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
                .padding(spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        val recording = voice.recording
        if (recording != null) {
            RecordingBar(recording, voice.drag, voice.onEvent, Modifier.weight(1f))
        } else {
            composer.sendAs?.let { SendAsButton(it, onSendAs) }
            ComposerPill(
                composer = composer,
                text = text,
                onSend = { send(SendOptions()) },
                onLink = { linking = it },
                onFocusChanged = onFocusChanged,
                attach = attach,
                panel = panel,
                focusRequester = focusRequester,
                canSend = canSend,
                modifier = Modifier.weight(1f),
            )
        }
        ComposerAction(edit, canSend, composer.canSchedule, send, onSchedule = { scheduling = true }, voice)
    }
    linking?.let { selection -> TextLinkDialog(text, selection, onDone = { linking = null }) }
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
 * The composer's pill ([ComposerField] on `surfaceContainer`, one step above the bar's `surface`, as the top bar): the
 * attach "+" at its start where the chat takes attachments; the emoji button at its end, and the gallery while the
 * field is empty ([canSend] false) where the chat takes photos and the composer does not edit.
 */
@Suppress("LongParameterList") // The field's state, where its text and focus go, and its buttons.
@Composable
private fun ComposerPill(
    composer: ComposerUiState.Available,
    text: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    attach: AttachInput,
    panel: ComposerPanelState,
    focusRequester: FocusRequester,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    val gallery = composer.bar !is ComposerBar.Edit && AttachOption.Gallery in composer.attachOptions
    ComposerField(
        text = text,
        onSend = onSend,
        onLink = onLink,
        onFocusChanged = onFocusChanged,
        modifier = modifier.focusRequester(focusRequester),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingIcons =
            if (composer.attachOptions.isNotEmpty()) {
                { AttachButton(attach.onAttach) }
            } else {
                null
            },
        trailingIcons = {
            PanelButton(panel, focusRequester)
            if (gallery) Appearing(visible = !canSend) { GalleryButton(attach.onGallery) }
        },
    )
}

/** What the attach buttons open: the attach menu ([onAttach]) and the system photo picker ([onGallery]). */
private data class AttachInput(
    val onAttach: () -> Unit,
    val onGallery: () -> Unit,
)

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
internal fun RowScope.Appearing(
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

/** The edges the bottom bar keeps clear of: the keyboard and the navigation bar below, cutouts at the sides. */
internal val BOTTOM_EDGES = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

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
