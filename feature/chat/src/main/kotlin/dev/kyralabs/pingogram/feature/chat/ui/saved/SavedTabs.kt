package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * What the conversation shows below its top bar, on the bar's `surfaceContainer`: Saved Messages' tabs
 * ([SavedTabs]); null elsewhere. A slot rather than a part of the conversation's state, as only Saved Messages has
 * them and its tabs outlive the conversation each of them shows. Only the top bar reads it.
 */
internal val LocalConversationTabs = compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Saved Messages' tabs (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs"): "All" and the chats the user saved
 * messages from, as the chat list's folder tabs, a connected [ButtonGroup] of [ToggleButton]s in a horizontal scroll
 * that brings the selected tab into view and fades at its edges, so the group never overflows into its menu. Names of
 * chats are user content; the selected label `labelLargeEmphasized`, unchecked buttons on `surfaceContainerHighest`, a
 * step above the bar. Screen readers hear tabs. [onNearEnd] asks for more topics as the row nears its end.
 */
@Composable
internal fun SavedTabs(
    state: SavedTabsUiState,
    onSelect: (Long?) -> Unit,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
    margin: Dp = windowMargin(),
) {
    val tabs = state.tabs
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val labels = tabs.map { it.title.resolve() }
    val scroll =
        rememberTabScroll(tabs.size, selectedIndex = tabs.indexOfFirst { it.topicId == state.selectedTopicId }, margin)
    NearEndEffect(scroll.state, onNearEnd)
    ButtonGroup(
        // Never measured: the group always fits in its scroll.
        overflowIndicator = {},
        modifier =
            modifier
                .fillMaxWidth()
                .fadingEdges(scroll.state)
                .horizontalScroll(scroll.state)
                .selectableGroup()
                // A 48 dp row: the buttons' touch targets, 4 dp above and below their 40 dp containers.
                .padding(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = tab.topicId == state.selectedTopicId
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { onSelect(tab.topicId) },
                        modifier =
                            Modifier
                                .animateWidth(interactionSource)
                                .onPlaced { scroll.buttonBounds[index] = it.boundsInParent() }
                                .semantics { role = Role.Tab },
                        shapes = connectedShapes(index, tabs.lastIndex),
                        colors = colors,
                        interactionSource = interactionSource,
                    ) {
                        val typography = MaterialTheme.typography
                        val style = if (selected) typography.labelLargeEmphasized else typography.labelLarge
                        Text(text = labels[index], maxLines = 1, style = style.userContent)
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(labels[index]) },
                        onClick = {
                            onSelect(tab.topicId)
                            menu.dismiss()
                        },
                    )
                },
            )
        }
    }
}

/** Calls [onNearEnd] whenever the row comes within a screen of its end. */
@Composable
private fun NearEndEffect(
    state: ScrollState,
    onNearEnd: () -> Unit,
) {
    val nearEnd by rememberUpdatedState(onNearEnd)
    LaunchedEffect(state) {
        snapshotFlow { state.maxValue - state.value < state.viewportSize }
            .distinctUntilChanged()
            .filter { it }
            .collect { nearEnd() }
    }
}

@Composable
private fun connectedShapes(
    index: Int,
    lastIndex: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

@PingogramPreviews
@Composable
private fun SavedTabsPreview() {
    PingogramPreview {
        SavedTabs(
            state =
                SavedTabsUiState(
                    tabs =
                        persistentListOf(
                            SavedTab(null, UiText.resource(R.string.saved_tab_all)),
                            SavedTab(PREVIEW_MY_NOTES, UiText.resource(R.string.saved_tab_my_notes)),
                            SavedTab(PREVIEW_CHANNEL, UiText.Plain("Android Developers")),
                            SavedTab(PREVIEW_ADA, UiText.Plain("Ada Lovelace")),
                            SavedTab(PREVIEW_HIDDEN, UiText.resource(R.string.saved_tab_author_hidden)),
                        ),
                    selectedTopicId = PREVIEW_CHANNEL,
                ),
            onSelect = {},
            onNearEnd = {},
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
        )
    }
}

private const val PREVIEW_MY_NOTES = 1L
private const val PREVIEW_CHANNEL = 2L
private const val PREVIEW_ADA = 3L
private const val PREVIEW_HIDDEN = 4L
