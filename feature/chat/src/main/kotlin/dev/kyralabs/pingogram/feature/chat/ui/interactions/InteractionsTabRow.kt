package dev.kyralabs.pingogram.feature.chat.ui.interactions

import android.content.res.Resources
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.fadingEdges
import dev.kyralabs.pingogram.core.ui.component.rememberTabScroll
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReactionGlyph
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import kotlinx.collections.immutable.ImmutableList

/**
 * The sheet's tabs, as the shared media's and the chat list's folder tabs (`docs/DESIGN_SYSTEM.md` §6 "Folder tabs"):
 * a connected [ButtonGroup] of [ToggleButton]s in a horizontal scroll that brings the selected tab into view and fades
 * at its edges, so the group never overflows into its menu, however many kinds of reaction the message has. Each tab
 * shows its label ("Seen", "All") or its reaction, then its count where known; the selected label is
 * `labelLargeEmphasized`, unchecked buttons on `surfaceContainerHighest`. Screen readers hear tabs, each with its
 * count ("Seen by 12 people", "❤, 3 reactions").
 */
@Composable
internal fun InteractionsTabRow(
    tabs: ImmutableList<InteractionsTabItem>,
    selected: InteractionsTab,
    onSelect: (InteractionsTab) -> Unit,
    modifier: Modifier = Modifier,
    margin: Dp = MaterialTheme.spacing.large,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val resources = LocalResources.current
    val scroll = rememberTabScroll(tabs.size, selectedIndex = tabs.indexOfFirst { it.tab == selected }, margin)
    ButtonGroup(
        // Never measured: the group always fits in its scroll.
        overflowIndicator = {},
        modifier =
            modifier
                .fillMaxWidth()
                .fadingEdges(scroll.state)
                .horizontalScroll(scroll.state)
                .selectableGroup()
                .padding(horizontal = margin, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, item ->
            val isSelected = item.tab == selected
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onSelect(item.tab) },
                        modifier =
                            Modifier
                                .animateWidth(interactionSource)
                                .onPlaced { scroll.buttonBounds[index] = it.boundsInParent() }
                                .semantics {
                                    role = Role.Tab
                                    contentDescription = item.spoken(resources)
                                },
                        shapes = connectedShapes(index, tabs.lastIndex),
                        colors = colors,
                        interactionSource = interactionSource,
                    ) {
                        val color = if (isSelected) colors.checkedContentColor else colors.contentColor
                        TabLabel(item, isSelected) { color }
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(item.spoken(resources)) },
                        onClick = {
                            onSelect(item.tab)
                            menu.dismiss()
                        },
                    )
                },
            )
        }
    }
}

/** A tab's label or reaction, then its count where known, hidden from screen readers: the tab says it. */
@Composable
private fun TabLabel(
    item: InteractionsTabItem,
    isSelected: Boolean,
    color: ColorProducer,
) {
    val typography = MaterialTheme.typography
    val style = if (isSelected) typography.labelLargeEmphasized else typography.labelLarge
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val tab = item.tab) {
            InteractionsTab.Seen -> {
                Text(stringResource(R.string.interactions_tab_seen), maxLines = 1, style = style)
            }

            InteractionsTab.AllReactions -> {
                Text(stringResource(R.string.interactions_tab_all), maxLines = 1, style = style)
            }

            is InteractionsTab.OneReaction -> {
                // In the label's style, so every tab is as tall as the others.
                ReactionGlyph(
                    label = tab.label,
                    style = style,
                    customEmojiSize = MaterialTheme.sizes.inlineIconSmall,
                    placeholderColor = color,
                    contentColor = color,
                )
            }
        }
        item.count?.let { Text(it.toString(), maxLines = 1, style = style.copy(fontFeatureSettings = TABULAR_FIGURES)) }
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

/** What screen readers hear for a tab: "Seen by 12 people", "All reactions, 25", "❤, 3 reactions". */
private fun InteractionsTabItem.spoken(resources: Resources): String {
    val count = count
    return when (val tab = tab) {
        InteractionsTab.Seen -> {
            if (count == null) {
                resources.getString(R.string.interactions_title_seen)
            } else {
                resources.getQuantityString(R.plurals.interactions_tab_seen_count, count, count)
            }
        }

        InteractionsTab.AllReactions -> {
            resources.getQuantityString(R.plurals.interactions_tab_all_count, count ?: 0, count ?: 0)
        }

        is InteractionsTab.OneReaction -> {
            val what = tab.label.spoken(resources)
            resources.getQuantityString(R.plurals.interactions_tab_reaction_count, count ?: 0, what, count ?: 0)
        }
    }
}

/** A reaction as screen readers say it: its emoji, "custom emoji" or "Star". */
internal fun ReactionLabel.spoken(resources: Resources): String =
    when (this) {
        is ReactionLabel.Emoji -> emoji
        is ReactionLabel.CustomEmoji -> resources.getString(R.string.chat_reaction_custom_emoji)
        ReactionLabel.Paid -> resources.getString(R.string.chat_reaction_paid)
    }

/** Counts in tabular figures, so they keep their width (`docs/DESIGN_SYSTEM.md` §3.3). */
private const val TABULAR_FIGURES = "tnum"
