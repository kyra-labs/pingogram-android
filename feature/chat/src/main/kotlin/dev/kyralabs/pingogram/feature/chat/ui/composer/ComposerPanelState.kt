package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
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
import androidx.compose.material3.SearchBarDefaults
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
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
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
 * - While a field of its own has the focus ([searching]: the GIF tab's search), the keyboard comes up below it and the
 *   panel stays, above the keyboard ([PanelArea]), instead of giving way to it.
 *
 * The same place shows a bot's keyboard ([content] [PanelContent.BotKeyboard], `BotReplyKeyboardPanel`), which back
 * closes unless the bot keeps it on screen ([closesOnBack] false).
 */
@Stable
internal class ComposerPanelState(
    open: Boolean = false,
    keyboardHeight: Int = 0,
    content: PanelContent = PanelContent.Emoji,
) {
    var isOpen by mutableStateOf(open)
        private set

    /** What the panel shows while open. */
    var content by mutableStateOf(content)
        private set

    /** Whether back closes the open panel. */
    var closesOnBack by mutableStateOf(true)
        private set

    var keyboardHeight by mutableIntStateOf(keyboardHeight)
        internal set

    /** A field inside the open panel has the focus, so the keyboard types into the panel and does not replace it. */
    var searching by mutableStateOf(false)

    /** The keyboard was asked for and the panel waits for it to be up. */
    var awaitsKeyboard by mutableStateOf(false)
        private set

    fun open(
        content: PanelContent = PanelContent.Emoji,
        closesOnBack: Boolean = true,
    ) {
        isOpen = true
        awaitsKeyboard = false
        this.content = content
        this.closesOnBack = closesOnBack
    }

    /** Whether the open panel shows [content]. */
    fun shows(content: PanelContent): Boolean = isOpen && this.content == content

    fun showKeyboard() {
        if (isOpen) awaitsKeyboard = true
    }

    fun close() {
        isOpen = false
        awaitsKeyboard = false
        searching = false
    }

    companion object {
        val Saver =
            listSaver<ComposerPanelState, Int>(
                save = { listOf(if (it.isOpen) 1 else 0, it.keyboardHeight, it.content.ordinal) },
                restore = {
                    ComposerPanelState(
                        // The emoji panel only: a bot's keyboard opens again by its own rules.
                        open = it[0] == 1 && it[2] == PanelContent.Emoji.ordinal,
                        keyboardHeight = it[1],
                    )
                },
            )
    }
}

/** What the panel in the keyboard's place shows. */
internal enum class PanelContent {
    /** The emoji and sticker panel. */
    Emoji,

    /** A bot's keyboard. */
    BotKeyboard,
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
            if (panel.shows(PanelContent.Emoji)) {
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
        if (panel.shows(PanelContent.Emoji)) {
            Icon(painterResource(UiR.drawable.ic_keyboard), stringResource(R.string.panel_keyboard))
        } else {
            Icon(painterResource(UiR.drawable.ic_mood), stringResource(R.string.panel_open))
        }
    }
}

/** Where the panel's stickers ([onSticker]) and GIFs ([onAnimation]) go: sent into the conversation. */
internal class PanelSends(
    val onSticker: (Sticker) -> Unit,
    val onAnimation: (Animation) -> Unit,
)

/**
 * The emoji and sticker panel ([EmojiPanel], showing [panel]) in the keyboard's place ([PanelArea], [panelState]): its
 * emoji go into [text] at the cursor, its stickers and GIFs to [sends] (shown only with [showStickers]), and what it
 * loads goes to [onPanelEvent].
 */
@Suppress("LongParameterList") // The panel's state and place, the text it types into, and where its events go.
@Composable
internal fun ComposerEmojiPanel(
    panelState: ComposerPanelState,
    panel: EmojiPanelUiState,
    text: ComposerText,
    onPanelEvent: (EmojiPanelEvent) -> Unit,
    sends: PanelSends,
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
            onSticker = sends.onSticker,
            onGif = sends.onAnimation,
            onSearchFocus = { panelState.searching = it },
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
    val insets =
        when {
            !panel.isOpen -> bottom
            panel.searching -> bottom.add(WindowInsets(bottom = searchBandHeight())).union(panelInsets(panel))
            else -> bottom.union(panelInsets(panel))
        }
    Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(insets)) {
        if (panel.isOpen) {
            // Searching, the keyboard comes up below the panel's own field: the content stays above it.
            val below = if (panel.searching) bottom else WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            Box(Modifier.fillMaxSize().windowInsetsPadding(below)) {
                content()
            }
        }
    }
}

@Composable
private fun panelInsets(panel: ComposerPanelState): WindowInsets = WindowInsets(bottom = panelHeight(panel))

/**
 * What a search inside the panel keeps above the keyboard, in pixels: its field and a row of results (the GIF tab's
 * search bar, `sizes.sharedMediaCell`), with their padding.
 */
@Composable
private fun searchBandHeight(): Int =
    with(LocalDensity.current) {
        (
            SearchBarDefaults.InputFieldHeight +
                MaterialTheme.sizes.sharedMediaCell +
                MaterialTheme.spacing.small * 2
        ).roundToPx()
    }

/**
 * How tall the open panel is, in pixels, from the bottom of the window: the keyboard's last height, or, before the
 * keyboard showed, `inputPanelHeight` above the navigation bar; never more than [MAX_PANEL_FRACTION] of the window
 * (a phone in landscape keeps room for the messages).
 */
@Composable
internal fun panelHeight(panel: ComposerPanelState): Int {
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
        val keyboard = snapshotFlow { Triple(ime.getBottom(density), imeTarget.getBottom(density), panel.searching) }
        keyboard.collect { (height, target, searching) ->
            // Fully up: the animation reached its target. Not on the way down, when the target is 0. A keyboard that
            // types into the panel's own field is not taller than the panel's place and does not replace it.
            if (height > 0 && height == target && !searching) {
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
    BackHandler(enabled = panel.isOpen && panel.closesOnBack) { panel.close() }
}

/** The most of the window the panel takes. */
private const val MAX_PANEL_FRACTION = 0.5f

/** How long the panel waits for the keyboard it asked for: longer than the keyboard's animation. */
private const val KEYBOARD_WAIT_MILLIS = 600L
