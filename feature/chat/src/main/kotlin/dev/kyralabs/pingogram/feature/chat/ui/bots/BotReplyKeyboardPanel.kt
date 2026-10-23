package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotKeyboardEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerPanelState
import dev.kyralabs.pingogram.feature.chat.ui.composer.PanelArea
import dev.kyralabs.pingogram.feature.chat.ui.composer.PanelContent
import dev.kyralabs.pingogram.feature.chat.ui.composer.panelHeight
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotKeyboardPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

// A bot's keyboard in the system keyboard's place (docs/DESIGN_SYSTEM.md §6 "Bot keyboard").

/**
 * Shows the bot's [keyboard] in the panel's place ([panel], [PanelContent.BotKeyboard]) by the keyboard's rules, while
 * the composer can use it ([enabled]; not while recording or editing):
 *
 * - It comes up whenever neither the system keyboard nor the emoji panel is (a new keyboard of the bot's, the chat
 *   opening with one, the system keyboard going down), unless the user put it away ([state]).
 * - The user puts it away by leaving it: for the system keyboard (the toggle, a tap in the field), the emoji panel or
 *   back. A persistent keyboard ([ReplyKeyboardUiState.persistent]) is never put away that way, and back passes by it.
 * - It closes once there is no keyboard, or the composer cannot use it.
 *
 * What it reads is read in a snapshot flow, never in composition: the keyboard's insets only lay the bar out again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BotKeyboardEffects(
    panel: ComposerPanelState,
    keyboard: ReplyKeyboardUiState?,
    enabled: Boolean,
    state: BotKeyboardState,
) {
    val imeTarget = WindowInsets.imeAnimationTarget
    val density = LocalDensity.current
    val current by rememberUpdatedState(keyboard?.takeIf { enabled })
    LaunchedEffect(panel, state, imeTarget, density) {
        var shown: ReplyKeyboardUiState? = null
        snapshotFlow {
            PanelSnapshot(
                keyboard = current,
                shows = panel.shows(PanelContent.BotKeyboard),
                isOpen = panel.isOpen,
                isImeUp = imeTarget.getBottom(density) > 0,
                hiddenFor = state.hiddenFor,
            )
        }.collect { snapshot ->
            val keyboardNow = snapshot.keyboard
            // Left for the system keyboard, the emoji panel or back: away until asked for.
            shown?.takeIf { snapshot.leaves(it) }?.let(state::hide)
            shown = keyboardNow.takeIf { snapshot.shows }
            when {
                keyboardNow == null -> {
                    if (snapshot.shows) panel.close()
                }

                snapshot.shows -> {
                    // The bot's next keyboard may keep itself on screen, or not.
                    if (panel.closesOnBack == keyboardNow.persistent) {
                        panel.open(PanelContent.BotKeyboard, closesOnBack = !keyboardNow.persistent)
                    }
                }

                !snapshot.isOpen && !snapshot.isImeUp && !state.isHidden(keyboardNow) -> {
                    panel.open(PanelContent.BotKeyboard, closesOnBack = !keyboardNow.persistent)
                }
            }
        }
    }
}

/**
 * What [BotKeyboardEffects] decides on: the usable keyboard, whether the panel shows it, whether the panel is open,
 * whether the system keyboard is up or coming, and the keyboard the user put away.
 */
private data class PanelSnapshot(
    val keyboard: ReplyKeyboardUiState?,
    val shows: Boolean,
    val isOpen: Boolean,
    val isImeUp: Boolean,
    val hiddenFor: Long,
) {
    /**
     * Whether the user left [shown], which the panel showed until now, while it is still in effect: it does not show
     * any more, and it does not keep itself on screen.
     */
    fun leaves(shown: ReplyKeyboardUiState): Boolean =
        !shows && keyboard?.messageId == shown.messageId && !shown.persistent
}

/**
 * The toggle in the composer's pill while a bot's keyboard is in effect: a grid that brings the bot's [keyboard] back
 * (`ic_grid_view`, "Show bot keyboard"), hiding the system keyboard; while it shows, a keyboard that brings the system
 * keyboard back and gives the field the focus ([focusRequester]), as the emoji button does. Each with a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class) // TooltipBox
@Composable
internal fun BotKeyboardToggle(
    panel: ComposerPanelState,
    keyboard: ReplyKeyboardUiState,
    state: BotKeyboardState,
    focusRequester: FocusRequester,
) {
    val systemKeyboard = LocalSoftwareKeyboardController.current
    val shows = panel.shows(PanelContent.BotKeyboard)
    val label = stringResource(if (shows) R.string.bot_keyboard_hide else R.string.bot_keyboard_show)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = {
                if (shows) {
                    panel.showKeyboard()
                    focusRequester.requestFocus()
                    systemKeyboard?.show()
                } else {
                    state.show()
                    panel.open(PanelContent.BotKeyboard, closesOnBack = !keyboard.persistent)
                    systemKeyboard?.hide()
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(if (shows) UiR.drawable.ic_keyboard else R.drawable.ic_grid_view), label)
        }
    }
}

/**
 * The bot's [keyboard] in the panel's place ([panel]): its rows as the inline keyboards' ([BotKeyboardRow], connected
 * `FilledTonalButton`s at the small height in 48 dp targets, `spacing.small` apart), keyed and scrolling where they
 * do not fit, no wider than a bubble can be and centred, on the composer's `surface`; screen readers hear it as a pane
 * ("Bot keyboard"). As tall as the system keyboard was, or, for a keyboard that may be resized
 * ([ReplyKeyboardUiState.resize]), only as tall as its rows need, up to that. A tap goes to [onPress]; a one-time
 * keyboard ([ReplyKeyboardUiState.oneTime]) puts itself away first ([state]), and the toggle brings it back.
 */
@Composable
internal fun BotReplyKeyboardPanel(
    panel: ComposerPanelState,
    keyboard: ReplyKeyboardUiState,
    state: BotKeyboardState,
    onPress: (BotButtonPosition) -> Unit,
) {
    val press = { position: BotButtonPosition ->
        if (keyboard.oneTime) {
            state.hide(keyboard)
            panel.close()
        }
        onPress(position)
    }
    if (keyboard.resize && panel.isOpen) {
        ResizedPanelArea(panel) { KeyboardRows(keyboard, press) }
    } else {
        PanelArea(panel) { KeyboardRows(keyboard, press) }
    }
}

/** The rows of [keyboard], a lazy column that scrolls where they do not fit. */
@Composable
private fun KeyboardRows(
    keyboard: ReplyKeyboardUiState,
    onPress: (BotButtonPosition) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val pane = stringResource(R.string.bot_keyboard_pane)
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                .semantics { paneTitle = pane },
        contentPadding = PaddingValues(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        itemsIndexed(keyboard.buttons.rows, key = { index, _ -> index }, contentType = { _, _ -> KEYBOARD_ROW }) {
            index,
            row,
            ->
            BotKeyboardRow(row, index, keyboard.buttons.pending, onPress)
        }
    }
}

/**
 * The panel's place for a keyboard that may be resized: as tall as [content] (above the navigation bar), at most the
 * panel's height, and at least the system keyboard's while it comes and goes, so the composer does not jump. Measured
 * in the layout phase, where the insets are read.
 */
@Composable
private fun ResizedPanelArea(
    panel: ComposerPanelState,
    content: @Composable () -> Unit,
) {
    val maxHeight = panelHeight(panel)
    val navigationBars = WindowInsets.navigationBars
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    Box(
        Modifier.fillMaxWidth().layout { measurable, constraints ->
            val navigationBar = navigationBars.getBottom(this)
            val rows =
                measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = (maxHeight - navigationBar).coerceAtLeast(0)),
                )
            val height = maxOf(rows.height + navigationBar, bottom.getBottom(this))
            layout(constraints.maxWidth, height) { rows.place(0, 0) }
        },
    ) {
        content()
    }
}

/**
 * The composer's view of the bot's keyboard in [reply]: the keyboard where it may show ([usable]: not while editing,
 * nor while an inline query shows its results), shown in [panel] by [BotKeyboardEffects] while [enabled], and the
 * field's hint.
 */
@Composable
internal fun composerBotKeyboard(
    panel: ComposerPanelState,
    reply: BotReplyUiState,
    usable: Boolean,
    enabled: Boolean,
): BotKeyboardInput {
    val state = rememberBotKeyboardState()
    val keyboard = reply.keyboard?.takeIf { usable }
    BotKeyboardEffects(panel, keyboard, enabled, state)
    return BotKeyboardInput(keyboard, state, reply.placeholder)
}

/**
 * The bot's [keyboard] in effect (null for none), for the pill's toggle and the panel, whether the user put it away
 * ([state]), and the field's hint the bot asks for ([placeholder], empty for the usual one).
 */
@Immutable
internal data class BotKeyboardInput(
    val keyboard: ReplyKeyboardUiState?,
    val state: BotKeyboardState,
    val placeholder: String = "",
)

/**
 * The panel in the keyboard's place: the bot's keyboard ([BotReplyKeyboardPanel], its taps to [onKeyboardPress]) while
 * the panel shows it, else the emoji and sticker panel ([emojiPanel]).
 */
@Composable
internal fun PanelPlace(
    panelState: ComposerPanelState,
    botKeyboard: BotKeyboardInput,
    onKeyboardPress: (BotButtonPosition) -> Unit,
    emojiPanel: @Composable () -> Unit,
) {
    val keyboard = botKeyboard.keyboard
    if (keyboard != null && panelState.content == PanelContent.BotKeyboard) {
        BotReplyKeyboardPanel(panelState, keyboard, botKeyboard.state, onKeyboardPress)
    } else {
        emojiPanel()
    }
}

/**
 * What a tap on a button of the bot's keyboard in [botReply] does: it goes to [onEvent], and one that sends its text
 * reports through [onSent], which scrolls to the newest message.
 */
internal fun keyboardPresses(
    botReply: BotReplyUiState,
    onEvent: (ChatEvent) -> Unit,
    onSent: (SendOptions) -> Unit,
): (BotButtonPosition) -> Unit =
    { position ->
        botReply.keyboard?.let { keyboard ->
            onEvent(BotKeyboardEvent.Press(keyboard.messageId, position))
            if (position in keyboard.sendsText) onSent(SendOptions())
        }
    }

/** The content type of a keyboard's rows. */
private const val KEYBOARD_ROW = "row"

@PingogramPreviews
@Composable
private fun BotReplyKeyboardPreview() {
    PingogramPreview {
        KeyboardRows(BotKeyboardPreviewData.keyboard, onPress = {})
    }
}
