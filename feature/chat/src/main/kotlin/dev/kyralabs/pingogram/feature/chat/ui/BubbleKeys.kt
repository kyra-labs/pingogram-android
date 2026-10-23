package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch

/**
 * A bubble row for the keyboard (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse", `docs/audit/accessibility.md` A1):
 * focusable where the keyboard moves the focus (Tab, the arrows), as Material's clickables are, and not by touch; its
 * focus goes to [interactionSource], which the bubble draws as a focus ring ([bubbleFocusRing]). Enter, the Menu key
 * and Shift+F10 open the message's actions ([opener]) from the row's place in the window, the floating menu taking the
 * focus and giving it back to the row when it closes; in selection mode ([selecting]) Enter selects or deselects it
 * ([toggle]) instead. Keys act when released, as Material's buttons do, so the release does not reach the menu. A
 * [Modifier.Node] added once per row: nothing composes or allocates per key or per frame.
 */
internal fun Modifier.bubbleKeys(
    interactionSource: MutableInteractionSource,
    selecting: Boolean,
    toggle: () -> Unit,
    opener: ActionsOpener,
): Modifier = this then BubbleKeysElement(interactionSource, selecting, toggle, opener)

private data class BubbleKeysElement(
    private val interactionSource: MutableInteractionSource,
    private val selecting: Boolean,
    private val toggle: () -> Unit,
    private val opener: ActionsOpener,
) : ModifierNodeElement<BubbleKeysNode>() {
    override fun create() = BubbleKeysNode(interactionSource, selecting, toggle, opener)

    override fun update(node: BubbleKeysNode) {
        node.update(interactionSource, selecting, toggle, opener)
    }
}

private class BubbleKeysNode(
    interactionSource: MutableInteractionSource,
    var selecting: Boolean,
    var toggle: () -> Unit,
    var opener: ActionsOpener,
) : DelegatingNode(),
    KeyInputModifierNode,
    SemanticsModifierNode {
    private var interactionSource = interactionSource

    // Focusable where the keyboard moves the focus, as Material's clickables are; not by touch.
    private val focusTarget =
        delegate(FocusTargetModifierNode(Focusability.SystemDefined) { _, current -> onFocused(current.isFocused) })

    /** The focus reported to [interactionSource], until the row loses it. */
    private var focus: FocusInteraction.Focus? = null

    /** The key whose press this row took, until it is released. */
    private var pressed: Key? = null

    fun update(
        interactionSource: MutableInteractionSource,
        selecting: Boolean,
        toggle: () -> Unit,
        opener: ActionsOpener,
    ) {
        if (interactionSource != this.interactionSource) {
            unfocus()
            this.interactionSource = interactionSource
        }
        this.selecting = selecting
        this.toggle = toggle
        this.opener = opener
    }

    override fun onDetach() {
        unfocus()
    }

    // As Material's focusables: whether the row has the focus, and a way to give it the focus.
    override fun SemanticsPropertyReceiver.applySemantics() {
        focused = focusTarget.focusState.isFocused
        requestFocus {
            focusTarget.requestFocus()
        }
    }

    /** Reports the focus for the ring, and scrolls the list to show the whole row, as Material's focusables do. */
    private fun onFocused(focused: Boolean) {
        if (isAttached) invalidateSemantics()
        if (!focused) return unfocus()
        if (focus != null) return
        val interaction = FocusInteraction.Focus()
        focus = interaction
        interactionSource.tryEmit(interaction)
        if (isAttached) coroutineScope.launch { bringIntoView() }
    }

    private fun unfocus() {
        val interaction = focus ?: return
        focus = null
        interactionSource.tryEmit(FocusInteraction.Unfocus(interaction))
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = event.key
        val acts =
            if (selecting) {
                key.isEnter()
            } else {
                key.isEnter() || key == Key.Menu ||
                    (key == Key.F10 && event.isShiftPressed)
            }
        if (!acts) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                pressed = key
                true
            }

            KeyEventType.KeyUp -> {
                val wasPressed = pressed == key
                pressed = null
                if (wasPressed) act()
                wasPressed
            }

            else -> {
                false
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    private fun act() {
        if (selecting) {
            toggle()
            return
        }
        opener.open(windowBounds(), takesFocus = true, returnFocus = ::returnFocus)
    }

    /** Back to this row once the menu is gone, after the frame that removes it, if the row is still there. */
    private fun returnFocus() {
        if (isAttached) coroutineScope.launch { if (isAttached) focusTarget.requestFocus() }
    }

    private fun windowBounds(): Rect {
        val coordinates = requireLayoutCoordinates()
        return Rect(coordinates.positionInWindow(), coordinates.size.toSize())
    }
}

private fun Key.isEnter(): Boolean = this == Key.Enter || this == Key.NumPadEnter

/**
 * The focus ring of a bubble whose row has the keyboard's focus ([bubbleKeys] reports it to [interactionSource]):
 * Material's ring of the theme (`RippleDefaults.InsetFocusRingThemeConfiguration`, `PingogramTheme`) in the bubble's
 * [shape], drawn over it in the draw phase; no pressed or hover layer, as the bubble is not a button.
 */
internal fun Modifier.bubbleFocusRing(
    interactionSource: MutableInteractionSource?,
    shape: Shape,
): Modifier =
    if (interactionSource == null) {
        this
    } else {
        indication(
            interactionSource,
            ripple(
                focusRingShape = shape,
                enablePressIndication = false,
                enableHoverIndication = false,
                enableDragIndication = false,
            ),
        )
    }
