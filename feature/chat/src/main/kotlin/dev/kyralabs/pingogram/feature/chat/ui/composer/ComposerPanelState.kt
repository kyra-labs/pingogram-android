package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanel
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import kotlinx.coroutines.delay
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Whether the emoji and sticker panel shows in the keyboard's place below the composer (`REQUIREMENTS.md` FR-C5), and
 * how tall the keyboard was when it last showed ([keyboardHeight], in pixels, 0 until then), which the panel takes, so
 * switching between them moves nothing. Kept across rotations and process death.
 *
 * - [open] shows the panel; the caller hides the keyboard, which goes down behind it.
 * - [showKeyboard] asks for the keyboard back: the panel stays until the keyboard is fully up ([ComposerPanelEffects]),
 *   then leaves with nothing to see, the keyboard in its place.
 * - [close] removes it at once: back, a recording, selection mode, a composer that goes away.
 */
@Stable
internal class ComposerPanelState(
    open: Boolean = false,
    keyboardHeight: Int = 0,
) {
    var isOpen by mutableStateOf(open)
        private set

    var keyboardHeight by mutableIntStateOf(keyboardHeight)
        internal set

    /** The keyboard was asked for and the panel waits for it to be up. */
    var awaitsKeyboard by mutableStateOf(false)
        private set

    fun open() {
        isOpen = true
        awaitsKeyboard = false
    }

    fun showKeyboard() {
        if (isOpen) awaitsKeyboard = true
    }

    fun close() {
        isOpen = false
        awaitsKeyboard = false
    }

    companion object {
        val Saver =
            listSaver<ComposerPanelState, Int>(
                save = { listOf(if (it.isOpen) 1 else 0, it.keyboardHeight) },
                restore = { ComposerPanelState(open = it[0] == 1, keyboardHeight = it[1]) },
            )
    }
}

@Composable
internal fun rememberComposerPanelState(): ComposerPanelState =
    rememberSaveable(saver = ComposerPanelState.Saver) { ComposerPanelState() }

/**
 * The button at the start of the text field: a smiley that opens the panel, hiding the keyboard; while the panel
 * shows, a keyboard that brings the keyboard back and gives the field the focus ([focusRequester]).
 */
@Composable
internal fun PanelButton(
    panel: ComposerPanelState,
    focusRequester: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    IconButton(
        onClick = {
            if (panel.isOpen) {
                panel.showKeyboard()
                focusRequester.requestFocus()
                keyboard?.show()
            } else {
                panel.open()
                keyboard?.hide()
            }
        },
        shapes = IconButtonDefaults.shapes(),
    ) {
        if (panel.isOpen) {
            Icon(painterResource(UiR.drawable.ic_keyboard), stringResource(R.string.panel_keyboard))
        } else {
            Icon(painterResource(UiR.drawable.ic_mood), stringResource(R.string.panel_open))
        }
    }
}

/**
 * The emoji and sticker panel ([EmojiPanel], showing [panel]) in the keyboard's place ([PanelArea], [panelState]): its
 * emoji go into [text] at the cursor, its stickers to [onSendSticker] (shown only with [showStickers]), and what it
 * loads goes to [onPanelEvent].
 */
@Suppress("LongParameterList") // The panel's state and place, the text it types into, and where its events go.
@Composable
internal fun ComposerEmojiPanel(
    panelState: ComposerPanelState,
    panel: EmojiPanelUiState,
    text: ComposerText,
    onPanelEvent: (EmojiPanelEvent) -> Unit,
    onSendSticker: (Sticker) -> Unit,
    showStickers: Boolean,
) {
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
            showStickers = showStickers,
        )
    }
}

/**
 * The space below the composer: the keyboard's and the navigation bar's (`safeDrawing`), or, while the panel is open,
 * the panel's [content] in at least the keyboard's last height ([panelHeight]) and as much as the keyboard takes while
 * it comes and goes, so the composer stays where it is. Its height is read in the layout phase, never in composition:
 * a keyboard that animates in or out only lays it out again.
 */
@Composable
internal fun PanelArea(
    panel: ComposerPanelState,
    content: @Composable () -> Unit,
) {
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    val insets = if (panel.isOpen) bottom.union(WindowInsets(bottom = panelHeight(panel))) else bottom
    Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(insets)) {
        if (panel.isOpen) {
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) {
                content()
            }
        }
    }
}

/**
 * How tall the open panel is, in pixels, from the bottom of the window: the keyboard's last height, or, before the
 * keyboard showed, `inputPanelHeight` above the navigation bar; never more than [MAX_PANEL_FRACTION] of the window
 * (a phone in landscape keeps room for the messages).
 */
@Composable
private fun panelHeight(panel: ComposerPanelState): Int {
    val density = LocalDensity.current
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    val default = with(density) { MaterialTheme.sizes.inputPanelHeight.roundToPx() } + navigationBar
    val height = panel.keyboardHeight.takeIf { it > 0 } ?: default
    val window = LocalWindowInfo.current.containerSize.height
    return if (window > 0) height.coerceAtMost((window * MAX_PANEL_FRACTION).toInt()) else height
}

/**
 * Keeps the panel and the keyboard in step: remembers the keyboard's height each time it is fully up, and then closes
 * the panel if it was open (the user tapped the field or the keyboard button); if no keyboard comes within
 * [KEYBOARD_WAIT_MILLIS] after the button (a hardware keyboard), the panel closes anyway. Back closes the panel. A
 * composer that cannot use it ([enabled] false: recording, nothing to write in) closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerPanelEffects(
    panel: ComposerPanelState,
    enabled: Boolean,
) {
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val density = LocalDensity.current
    LaunchedEffect(panel, ime, imeTarget, density) {
        snapshotFlow { ime.getBottom(density) to imeTarget.getBottom(density) }.collect { (height, target) ->
            // Fully up: the animation reached its target. Not on the way down, when the target is 0.
            if (height > 0 && height == target) {
                panel.keyboardHeight = height
                if (panel.isOpen) panel.close()
            }
        }
    }
    if (panel.awaitsKeyboard) {
        LaunchedEffect(panel) {
            delay(KEYBOARD_WAIT_MILLIS)
            if (panel.awaitsKeyboard && imeTarget.getBottom(density) == 0) panel.close()
        }
    }
    if (!enabled && panel.isOpen) {
        LaunchedEffect(panel) { panel.close() }
    }
    BackHandler(enabled = panel.isOpen) { panel.close() }
}

/** The most of the window the panel takes. */
private const val MAX_PANEL_FRACTION = 0.5f

/** How long the panel waits for the keyboard it asked for: longer than the keyboard's animation. */
private const val KEYBOARD_WAIT_MILLIS = 600L
