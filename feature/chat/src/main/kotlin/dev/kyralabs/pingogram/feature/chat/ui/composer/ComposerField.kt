package dev.kyralabs.pingogram.feature.chat.ui.composer

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.ui.sticker.CustomEmojiRenderer
import dev.kyralabs.pingogram.core.ui.sticker.LocalCustomEmoji
import dev.kyralabs.pingogram.core.ui.text.CustomEmojiField
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.customEmoji
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlin.math.roundToInt

/**
 * The text field: a filled tonal pill (`docs/DESIGN_SYSTEM.md` §6 Composer), a [BasicTextField]
 * with Material's text field decoration ([TextFieldDefaults.decorator], [TextFieldDefaults.tonalColors] on
 * [containerColor], by default `surfaceContainerHighest`, a step above a sheet) at `sizes.composerField` (56 dp) for
 * one line, `extraLarge` corners, which make one line a full pill; `bodyLarge`, its direction taken from what is typed
 * ([userContent]), [placeholder] ("Message") while empty, [leadingIcons] at its start and [trailingIcons] at its end
 * (the conversation's attach, emoji and gallery buttons), each a 48 dp touch target 4 dp inside the pill. Which Enter
 * sends ([onSend]) follows [LocalSendKeys]: a hardware keyboard's (Shift+Enter and Ctrl+Enter then start a new line;
 * else Ctrl+Enter sends and Enter starts one), and the on-screen keyboard's, whose action key then reads "Send" (else
 * it starts a new line). The selection menu, Ctrl+B/I/U/K and screen readers' actions format the selected text; "Link"
 * asks for an address ([onLink]). [onKeyEvent] sees every key first and takes those it handles (the bot command
 * suggestions' arrows, Enter and Tab). [hint] shows after the text in the placeholder's color, an inline bot's
 * placeholder after "@gif " ([InlineHint]). Tab and Shift+Tab move the focus on ([tabKey]). Custom emoji in the text (a
 * draft's, an edited message's) show their pictures over their fallback emoji, as in bubbles. The attach preview's
 * caption and the forward sheet's comment are one too.
 */
@Composable
internal fun ComposerField(
    text: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.composer_placeholder),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    leadingIcons: (@Composable RowScope.() -> Unit)? = null,
    trailingIcons: (@Composable RowScope.() -> Unit)? = null,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    hint: String? = null,
) {
    val labels = rememberFormattingLabels()
    val focusManager = LocalFocusManager.current
    val sendKeys = LocalSendKeys.current
    val textLayout = remember { TextLayoutHolder() }
    val scrollState = rememberScrollState()
    val customEmoji = rememberFieldCustomEmoji(text, scrollState)
    val hasSelection by remember(text) { derivedStateOf { !text.state.selection.collapsed } }
    // onFocusChanged also reports the first state, unfocused: only changes are news.
    val focus = remember { FocusReport() }
    val colors = pillColors(containerColor)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val outputTransformation = rememberFormattingTransformation(text)
    val textStyle =
        MaterialTheme.typography.bodyLarge.userContent
            .copy(color = if (focused) colors.focusedTextColor else colors.unfocusedTextColor)
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            state = text.state,
            modifier =
                modifier
                    .testTag(COMPOSER_FIELD_TEST_TAG)
                    .heightIn(min = MaterialTheme.sizes.composerField)
                    .onFocusChanged { state ->
                        if (state.isFocused != focus.isFocused) {
                            focus.isFocused = state.isFocused
                            onFocusChanged(state.isFocused)
                        }
                    }.onPreviewKeyEvent { event ->
                        onKeyEvent(event) || fieldKey(event, text, onSend, onLink, sendKeys) ||
                            tabKey(event, focusManager)
                    }.appendTextContextMenuComponents { formattingItems(text, labels, onLink) }
                    .semantics { if (hasSelection) customActions = formattingActions(text, labels, onLink) },
            textStyle = textStyle,
            inputTransformation = text.inputTransformation,
            outputTransformation = outputTransformation,
            keyboardOptions = sendKeys.keyboardOptions(),
            onKeyboardAction = sendKeys.keyboardAction(onSend),
            lineLimits = LINE_LIMITS,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.cursorColor),
            scrollState = scrollState,
            onTextLayout = { getResult -> textLayout.update(getResult(), customEmoji) },
            decorator =
                pillDecorator(
                    text = text,
                    outputTransformation = outputTransformation,
                    interactionSource = interactionSource,
                    colors = colors,
                    placeholder = placeholder,
                    icons = PillIcons(leadingIcons, trailingIcons),
                    focused = focused,
                    hint = hint?.let { InlineHintText(it, textLayout) },
                    customEmoji = customEmoji,
                ),
        )
    }
}

/**
 * The text field's decoration: Material's filled text field in [colors] on an `extraLarge` (pill) container, without a
 * label: `spacing.extraSmall` around everything and `spacing.medium` above and below the text, 56 dp at one line as
 * `TextField`. The [icons] (in the colors' leading icon color while [focused] or not) sit at the pill's start and end,
 * outside Material's icon slots, which centre them on the field: they stay at the bottom, level with the last line, as
 * the field grows, as does the button beside the field.
 */
@Suppress("LongParameterList") // The decorator's own inputs: the field's state, its transformation and its slots.
@Composable
private fun pillDecorator(
    text: ComposerText,
    outputTransformation: OutputTransformation,
    interactionSource: MutableInteractionSource,
    colors: TextFieldColors,
    placeholder: String,
    icons: PillIcons,
    focused: Boolean,
    hint: InlineHintText?,
    customEmoji: FieldCustomEmoji?,
): TextFieldDecorator {
    val leadingIconColor = if (focused) colors.focusedLeadingIconColor else colors.unfocusedLeadingIconColor
    val shape = MaterialTheme.shapes.extraLarge
    val spacing = MaterialTheme.spacing
    val material =
        TextFieldDefaults.decorator(
            state = text.state,
            enabled = true,
            lineLimits = LINE_LIMITS,
            outputTransformation = outputTransformation,
            interactionSource = interactionSource,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
            colors = colors,
            contentPadding =
                TextFieldDefaults.contentPaddingWithoutLabel(
                    // Beside an icon's 48 dp touch target, as far from it as Material's icon slot keeps the text;
                    // else 16 dp from the pill's side, with the padding around everything.
                    start = if (icons.leading != null) spacing.extraSmall else spacing.medium,
                    top = spacing.medium,
                    end = if (icons.trailing != null) spacing.extraSmall else spacing.medium,
                    bottom = spacing.medium,
                ),
            // Drawn below, behind the icon too.
            container = {},
        )
    return TextFieldDecorator { innerTextField ->
        Box {
            TextFieldDefaults.Container(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                modifier = Modifier.matchParentSize(),
                colors = colors,
                shape = shape,
            )
            Row(Modifier.padding(spacing.extraSmall), verticalAlignment = Alignment.Bottom) {
                icons.leading?.let { leading ->
                    CompositionLocalProvider(LocalContentColor provides leadingIconColor) { leading() }
                }
                Box(Modifier.weight(1f)) {
                    material.Decoration {
                        val emojiModifier =
                            customEmoji?.let { Modifier.customEmoji(it.renderer, it.layout, it.tint, it.field) }
                        Box(emojiModifier ?: Modifier) {
                            innerTextField()
                            hint?.let { InlineHint(it) }
                        }
                    }
                }
                icons.trailing?.let { trailing ->
                    CompositionLocalProvider(LocalContentColor provides leadingIconColor) { trailing() }
                }
            }
        }
    }
}

/**
 * The custom emoji of the field's text (a draft's, a message being edited), drawn over their fallback emoji from the
 * field's layout as bubbles draw theirs ([customEmoji]), with the text's entities and the field's scroll; null where
 * the screen draws no custom emoji ([LocalCustomEmoji]).
 */
@Composable
private fun rememberFieldCustomEmoji(
    text: ComposerText,
    scrollState: ScrollState,
): FieldCustomEmoji? {
    val renderer = LocalCustomEmoji.current ?: return null
    val color = MaterialTheme.colorScheme.onSurface
    return remember(renderer, text, scrollState, color) {
        FieldCustomEmoji(
            renderer = renderer,
            layout = TextBlocksLayout(),
            tint = { color },
            field = CustomEmojiField(entities = { text.content.entities }, scrollOffset = { scrollState.value }),
        )
    }
}

/** What the field's [customEmoji] modifier draws with. */
private class FieldCustomEmoji(
    val renderer: CustomEmojiRenderer,
    val layout: TextBlocksLayout,
    val tint: ColorProducer,
    val field: CustomEmojiField,
)

/** The text field's latest text layout, for what is drawn after its text ([InlineHint]). */
@Stable
private class TextLayoutHolder {
    var result by mutableStateOf<TextLayoutResult?>(null)

    /** Keeps the field's new layout [result], for [customEmoji] too. */
    fun update(
        result: TextLayoutResult?,
        customEmoji: FieldCustomEmoji?,
    ) {
        this.result = result
        if (result != null) customEmoji?.layout?.onTextLayout(result)
    }
}

/** A hint ([text]) drawn after the text of the field whose layout [layout] holds. */
private class InlineHintText(
    val text: String,
    val layout: TextLayoutHolder,
)

/**
 * [hint]'s text in the placeholder's style (`bodyLarge`, `onSurfaceVariant`) right after the field's text, on its last
 * line, ending in "…" where it does not fit; placed in the layout phase from the text's layout, so typing moves it
 * without composing it again. Beside text that runs left to right it follows on the right, whatever the layout's
 * direction, as the text does.
 */
@Composable
private fun InlineHint(hint: InlineHintText) {
    Text(
        text = hint.text,
        modifier =
            Modifier.layout { measurable, constraints ->
                val text = hint.layout.result
                val end = text?.layoutInput?.text?.length ?: 0
                val x = text?.getHorizontalPosition(end, usePrimaryDirection = true)?.roundToInt() ?: 0
                val top = text?.let { it.getLineTop(it.lineCount - 1).roundToInt() } ?: 0
                val width = (constraints.maxWidth - x).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width, minHeight = 0))
                layout(0, 0) { placeable.place(x, top) }
            },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge.userContent,
    )
}

/** Material's tonal text field colors on [containerColor], focused or not. */
@Composable
private fun pillColors(containerColor: Color) =
    TextFieldDefaults.tonalColors().copy(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
    )

/** The buttons at the start and the end of the pill. */
private class PillIcons(
    val leading: (@Composable RowScope.() -> Unit)?,
    val trailing: (@Composable RowScope.() -> Unit)?,
)

/** Whether the text field had the focus when it last reported it; read in callbacks only, so not snapshot state. */
private class FocusReport(
    var isFocused: Boolean = false,
)

/**
 * Enter pressed on a hardware keyboard, without Alt or Meta. The on-screen keyboard sends its Enter as a key event too,
 * flagged as its own, which its action ([LocalSendKeys]) handles instead.
 */
private fun KeyEvent.isHardwareEnter(): Boolean =
    type == KeyEventType.KeyDown &&
        (key == Key.Enter || key == Key.NumPadEnter) &&
        !isAltPressed &&
        !isMetaPressed &&
        nativeKeyEvent.flags and FLAG_SOFT_KEYBOARD == 0

/**
 * What the field does with [event] itself; true if handled. Enter sends ([onSend]) where [SendKeys.hardwareKeyboard],
 * and Ctrl+Enter starts a new line, which the field would not; else Ctrl+Enter sends. Enter and Shift+Enter left to the
 * field start a new line. Ctrl shortcuts format [text].
 */
private fun fieldKey(
    event: KeyEvent,
    text: ComposerText,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
    sendKeys: SendKeys,
): Boolean {
    if (!event.isHardwareEnter()) return formattingShortcut(event, text, onLink)
    val enterSends = sendKeys.hardwareKeyboard
    val ctrl = event.isCtrlPressed && !event.isShiftPressed
    val plain = !event.isCtrlPressed && !event.isShiftPressed
    return when {
        if (enterSends) plain else ctrl -> true.also { onSend() }
        enterSends && ctrl -> true.also { text.insert("\n") }
        else -> false
    }
}

/** The on-screen keyboard's options: sentences, and a "Send" action key where [SendKeys.onScreenKeyboard]. */
private fun SendKeys.keyboardOptions() =
    KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = if (onScreenKeyboard) ImeAction.Send else ImeAction.Default,
    )

/** What the on-screen keyboard's action key does: sends where [SendKeys.onScreenKeyboard], else the field's default. */
private fun SendKeys.keyboardAction(onSend: () -> Unit): KeyboardActionHandler? =
    if (onScreenKeyboard) KeyboardActionHandler { onSend() } else null

/**
 * Tab and Shift+Tab move the focus on to the next or previous control, as they do from Android's text fields, rather
 * than typing a tab: a message has no use for one, and the keyboard would be stuck in the field
 * (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"). True if handled.
 */
private fun tabKey(
    event: KeyEvent,
    focusManager: FocusManager,
): Boolean {
    val withModifier = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
    if (event.key != Key.Tab || withModifier) return false
    if (event.type == KeyEventType.KeyDown) {
        focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
    }
    return true
}

/** The lines the text field grows to before it scrolls. */
private const val MAX_LINES = 6

/** The text field's lines: as many as the text has, up to [MAX_LINES], then it scrolls. */
private val LINE_LIMITS = TextFieldLineLimits.MultiLine(maxHeightInLines = MAX_LINES)

/** The composer's field, for `benchmark/`'s journeys (`MainActivity` shows test tags as resource ids). */
internal const val COMPOSER_FIELD_TEST_TAG = "composer_field"
