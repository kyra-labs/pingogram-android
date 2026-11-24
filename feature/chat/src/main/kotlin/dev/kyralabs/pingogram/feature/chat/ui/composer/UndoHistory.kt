package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the message box's menu can take back or redo (`REQUIREMENTS.md` FR-X13): its text field's own history
 * ([TextFieldState.undoState]); [None] while Settings → Chats → "Undo and redo" is off.
 */
@Immutable
internal data class UndoHistory(
    val canUndo: Boolean,
    val canRedo: Boolean,
) {
    companion object {
        val None = UndoHistory(canUndo = false, canRedo = false)
    }
}

/** What [state]'s history allows now, while Settings → Chats → "Undo and redo" is on ([LocalShowsUndoRedo]). */
@OptIn(ExperimentalFoundationApi::class) // TextFieldState.undoState.
@Composable
internal fun rememberUndoHistory(state: TextFieldState): State<UndoHistory> {
    val showsUndoRedo = LocalShowsUndoRedo.current
    return remember(state, showsUndoRedo) {
        derivedStateOf {
            val undo = state.undoState
            if (showsUndoRedo) UndoHistory(undo.canUndo, undo.canRedo) else UndoHistory.None
        }
    }
}

/** "Undo" and "Redo" in the field's menu, each while [history] allows it, after a separator. */
@OptIn(ExperimentalFoundationApi::class) // TextFieldState.undoState.
internal fun TextContextMenuBuilderScope.undoItems(
    state: TextFieldState,
    history: UndoHistory,
    labels: FormattingLabels,
) {
    if (history == UndoHistory.None) return
    separator()
    if (history.canUndo) {
        item(key = UNDO_ITEM, label = labels.undo, leadingIcon = UiR.drawable.ic_undo) {
            state.undoState.undo()
            close()
        }
    }
    if (history.canRedo) {
        item(key = REDO_ITEM, label = labels.redo, leadingIcon = UiR.drawable.ic_redo) {
            state.undoState.redo()
            close()
        }
    }
}

/** The same for screen readers, as the field's custom actions. */
@OptIn(ExperimentalFoundationApi::class) // TextFieldState.undoState.
internal fun undoActions(
    state: TextFieldState,
    history: UndoHistory,
    labels: FormattingLabels,
): List<CustomAccessibilityAction> =
    listOfNotNull(
        CustomAccessibilityAction(labels.undo) { true.also { state.undoState.undo() } }.takeIf { history.canUndo },
        CustomAccessibilityAction(labels.redo) { true.also { state.undoState.redo() } }.takeIf { history.canRedo },
    )

private const val UNDO_ITEM = "undo"
private const val REDO_ITEM = "redo"
