package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanel
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.panel.GifCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
 *
 * Opened or closed with no keyboard coming or going, the panel grows in and shrinks away ([shown], driven by
 * [ComposerPanelEffects], `docs/DESIGN_SYSTEM.md` §5.1, Q-11); [isShown] keeps it on screen while it shrinks.
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

    /**
     * How much of the panel's height its place takes: 1 open, 0 closed, in between while it grows in or shrinks away
     * and while a back gesture previews closing it. Read in the layout phase ([PanelArea]), never in composition.
     */
    internal val shown = Animatable(if (open) 1f else 0f)

    /** Whether the panel is on screen: open, or still shrinking away. */
    val isShown: Boolean by derivedStateOf { isOpen || shown.value > 0f }

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

/**
 * Where the panel's stickers ([onSticker]) and GIFs ([onAnimation]) go: sent into the conversation, a GIF with the
 * composer's text as its caption where [gifCaption] says so.
 */
internal class PanelSends(
    val onSticker: (Sticker) -> Unit,
    val onAnimation: (Animation) -> Unit,
    val gifCaption: GifCaption? = null,
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
        // Not the bot's keyboard shrinking away in the emoji panel's place.
        if (panelState.content != PanelContent.Emoji) return@PanelArea
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
            gifCaption = sends.gifCaption,
        )
    }
}

/**
 * The space below the composer: the keyboard's and the navigation bar's (`safeDrawing`), or, while the panel is shown,
 * the panel's [content] in at least the keyboard's last height ([panelHeight]) and as much as the keyboard takes while
 * it comes and goes, so the composer stays where it is. Opened or closed with no keyboard involved, the panel's height
 * grows from the navigation bar's and shrinks back to it ([ComposerPanelState.shown]); [content] keeps its full height
 * meanwhile and is clipped, so it is not measured anew in every frame. Its height is read in the layout phase, never in
 * composition: a keyboard that animates in or out, or the panel growing, only lays it out again.
 */
@Composable
internal fun PanelArea(
    panel: ComposerPanelState,
    content: @Composable () -> Unit,
) {
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    val shown = panel.isShown
    val full = panelHeight(panel)
    val insets =
        when {
            !shown -> bottom
            panel.searching -> bottom.add(WindowInsets(bottom = searchBandHeight())).union(panelInsets(panel, full))
            else -> bottom.union(panelInsets(panel, full))
        }
    Box(Modifier.fillMaxWidth().clipToBounds().windowInsetsBottomHeight(insets)) {
        if (shown) {
            // Searching, the keyboard comes up below the panel's own field: the content stays above it.
            val below = if (panel.searching) bottom else WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            Box(Modifier.fillMaxWidth().atLeastHeight(full).windowInsetsPadding(below)) {
                content()
            }
        }
    }
}

/**
 * Lays this content out at least [height] pixels tall, from the top of its place: as tall as the panel is open while
 * its place grows or shrinks, the rest clipped.
 */
private fun Modifier.atLeastHeight(height: Int): Modifier =
    layout { measurable, constraints ->
        val tall = maxOf(constraints.maxHeight, height)
        val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/**
 * The open panel's height, [full] pixels from the bottom of the window, as much of it as [ComposerPanelState.shown]
 * says above the navigation bar: read in the layout phase, so the panel growing lays the composer out again and
 * recomposes nothing.
 */
@Composable
private fun panelInsets(
    panel: ComposerPanelState,
    full: Int,
): WindowInsets {
    val navigationBars = WindowInsets.navigationBars
    return remember(panel, full, navigationBars) { ShownPanelInsets(full, navigationBars) { panel.shown.value } }
}

/** [panelInsets]' insets: the navigation bar's, then [fraction] of the rest of [full]. */
private class ShownPanelInsets(
    private val full: Int,
    private val navigationBars: WindowInsets,
    private val fraction: () -> Float,
) : WindowInsets {
    override fun getBottom(density: Density): Int {
        val base = navigationBars.getBottom(density)
        return (base + (full - base) * fraction()).roundToInt().coerceAtLeast(0)
    }

    override fun getLeft(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = 0

    override fun getRight(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = 0

    override fun getTop(density: Density): Int = 0
}

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
 * [KEYBOARD_WAIT_MILLIS] after the button (a hardware keyboard), the panel closes anyway. A composer that cannot use it
 * ([enabled] false: recording, nothing to write in) closes it.
 *
 * The panel grows in and shrinks away on the default spatial spring when it opens or closes with no keyboard coming or
 * going (`docs/DESIGN_SYSTEM.md` §5.1, Q-11); in the keyboard's place it takes the keyboard's height at once, so the
 * switch moves nothing. Back closes it, the place following the gesture down to 90% of the panel's height on
 * Material's back-progress curve ([predictiveBackScale]) before it shrinks away; a cancelled gesture springs it back.
 * With reduced motion the gesture previews nothing.
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
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(panel, panel.isOpen) {
        val target = if (panel.isOpen) 1f else 0f
        // The keyboard comes or goes in the panel's place: it moves the composer already, the panel takes its height.
        val keyboard = ime.getBottom(density) > 0 || imeTarget.getBottom(density) > 0
        if (keyboard) panel.shown.snapTo(target) else panel.shown.animateTo(target, spec)
    }
    PanelBack(panel, spec)
}

/** Back closing the open [panel], previewed with the gesture; a cancelled gesture springs back on [spec]. */
@Composable
private fun PanelBack(
    panel: ComposerPanelState,
    spec: AnimationSpec<Float>,
) {
    val reducedMotion = isReducedMotion()
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = panel.isOpen && panel.closesOnBack) { events ->
        try {
            events.collect { event -> if (!reducedMotion) panel.shown.snapTo(predictiveBackScale(event.progress)) }
            panel.close()
        } catch (cancelled: CancellationException) {
            // A cancelled gesture, when this coroutine can no longer animate.
            scope.launch { panel.shown.animateTo(1f, spec) }
            throw cancelled
        }
    }
}

/** The most of the window the panel takes. */
private const val MAX_PANEL_FRACTION = 0.5f

/** How long the panel waits for the keyboard it asked for: longer than the keyboard's animation. */
private const val KEYBOARD_WAIT_MILLIS = 600L
