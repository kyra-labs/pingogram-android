package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected

/**
 * A tab of the panel for screen readers (`docs/audit/accessibility.md` A14): the toggle button inside it draws the tab,
 * and this node, which wraps it, is what screen readers hear: "[label], Tab, selected" rather than the toggle's
 * "Tab, checked". Its click selects the tab ([onSelect]), as the button's does.
 */
internal fun Modifier.panelTab(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
): Modifier =
    clearAndSetSemantics {
        role = Role.Tab
        this.selected = selected
        contentDescription = label
        onClick {
            onSelect()
            true
        }
    }
