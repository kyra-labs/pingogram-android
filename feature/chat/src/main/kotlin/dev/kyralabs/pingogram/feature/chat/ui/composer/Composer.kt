package dev.kyralabs.pingogram.feature.chat.ui.composer

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bottom of the conversation (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6): the [Composer] where the user
 * can write, a short centred label in `onSurfaceVariant` where they cannot, nothing in a channel they only read. It
 * is a `surfaceContainer` surface below the messages' sheet, which reaches under the keyboard and the system bars, edge
 * to edge, while its content stays above them.
 *
 * [onSend] gets what to send once the user asked for it; [onFocusChanged] whether the text field has the focus.
 */
@Composable
internal fun ChatBottomBar(
    composer: ComposerUiState,
    text: ComposerText,
    onSend: (SendOptions) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Instant = Instant::now,
) {
    // Nothing in a channel the user only reads, or while the chat is not known.
    if (composer == ComposerUiState.Hidden) return
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
                Composer(composer.canSchedule, text, onSend, onFocusChanged, now, insets)
            }

            ComposerUiState.Hidden -> {}
        }
    }
}

/**
 * The composer: a multi-line text field that grows to [MAX_LINES] lines and then scrolls, and once there is something
 * to send, the send split button ([SendButton]) beside it, which grows in on the motion scheme's spatial spring.
 * Formatting comes from the selection menu ([formattingItems]) and Ctrl shortcuts, a link from [LinkDialog], a time
 * from [ScheduleDialog]. Sending confirms with a haptic (`HapticFeedbackType.Confirm`).
 */
@Composable
private fun Composer(
    canSchedule: Boolean,
    text: ComposerText,
    onSend: (SendOptions) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    now: () -> Instant,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val send = { options: SendOptions ->
        if (!text.isBlank) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSend(options)
        }
    }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    val canSend by remember(text) { derivedStateOf { !text.isBlank } }
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        ComposerField(
            text = text,
            onSend = { send(SendOptions()) },
            onLink = { linking = it },
            onFocusChanged = onFocusChanged,
            modifier = Modifier.weight(1f),
        )
        Appearing(visible = canSend) {
            SendButton(
                canSchedule = canSchedule,
                onSend = { send(SendOptions()) },
                onSendSilently = { send(SendOptions(silent = true)) },
                onSchedule = { scheduling = true },
            )
        }
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
 * The text field: outlined, `extraLarge` corners, `bodyLarge`, "Message" while empty. Enter on a hardware keyboard
 * sends ([onSend]) and Shift+Enter starts a new line; the on-screen keyboard's Enter always starts one. The selection
 * menu, Ctrl+B/I/U/K and screen readers' actions format the selected text; "Link" asks for an address ([onLink]).
 */
@Composable
private fun ComposerField(
    text: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
        placeholder = { Text(stringResource(R.string.composer_placeholder)) },
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

/**
 * The send button, a [SplitButtonLayout] as tall as the text field: the leading button sends, the trailing one opens
 * the menu of other ways to send (without sound; at a time, where [canSchedule]). The trailing button's corners morph
 * to a full circle while the menu is open, and its arrow turns on the motion scheme's spatial spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendButton(
    canSchedule: Boolean,
    onSend: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val height = ButtonDefaults.MediumContainerHeight
    val sendLabel = stringResource(R.string.composer_send)
    val optionsLabel = stringResource(R.string.composer_send_options)
    val arrowTurn by animateFloatAsState(
        targetValue = if (menuOpen) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "sendOptionsArrow",
    )
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onSend,
                modifier = Modifier.heightIn(min = height).semantics { contentDescription = sendLabel },
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_send_filled),
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                )
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.heightIn(min = height).semantics { contentDescription = optionsLabel },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(SplitButtonDefaults.trailingButtonIconSizeFor(height))
                                .graphicsLayer { rotationZ = arrowTurn },
                    )
                }
                SendOptionsMenu(
                    expanded = menuOpen,
                    canSchedule = canSchedule,
                    onDismiss = { menuOpen = false },
                    onSendSilently = onSendSilently,
                    onSchedule = onSchedule,
                )
            }
        },
    )
}

/** The other ways to send, under the send button's trailing part: without sound, and at a time where [canSchedule]. */
@Composable
private fun SendOptionsMenu(
    expanded: Boolean,
    canSchedule: Boolean,
    onDismiss: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.composer_send_silently)) },
            onClick = {
                onDismiss()
                onSendSilently()
            },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_notifications_off), null) },
        )
        if (canSchedule) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_schedule)) },
                onClick = {
                    onDismiss()
                    onSchedule()
                },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_schedule), null) },
            )
        }
    }
}

/** The edges the bottom bar keeps clear of: the keyboard and the navigation bar below, cutouts at the sides. */
private val BOTTOM_EDGES = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom

/** The lines the text field grows to before it scrolls. */
private const val MAX_LINES = 6

/** How far the send menu's arrow turns while the menu is open, in degrees. */
private const val HALF_TURN = 180f

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
