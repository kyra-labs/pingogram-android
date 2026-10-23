package dev.kyralabs.pingogram.feature.calls.history

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A call of Recent calls (`docs/DESIGN_SYSTEM.md` §6), as the chat list's rows: an M3 [ListItem], Material's
 * two-line item at 72 dp, inset in its sheet; the 48 dp circular avatar, the name in `titleMedium` as user content,
 * and below it the direction's symbol (`call_received`, `call_made`, `call_missed`, `call_missed_outgoing`) with how
 * the call went and its length ("Outgoing video call · 4:05") in `bodyMedium`, a missed call's symbol and words in
 * `error`; at the end
 * the time (`labelSmall`, tabular figures) over the symbol of what a tap does (`call`, `videocam`).
 *
 * A tap calls back the way the call went ([onClick]); a long press opens a menu with "Delete from call history"
 * ([onDelete], in `error`), the row in the list item's selected colors and `large` shape while it is open. Screen
 * readers hear one button ("Ada Lovelace, Missed call, 9:41 AM"), "Call back" as its click label and the delete as its
 * custom action, built only when something reads the semantics tree.
 */
@Composable
internal fun RecentCallRow(
    call: RecentCallsItem.Call,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable(call.key) { mutableStateOf(false) }
    val resources = LocalResources.current
    val insets = rowInsets()
    val colors = rowColors(selected = menuOpen)
    val shapes = ListItemDefaults.shapes()
    val name = call.name.resolve()
    Box(modifier) {
        ListItem(
            onClick = onClick,
            modifier = Modifier.padding(insets).rowSemantics(call, name, resources, onClick, onDelete),
            onLongClick = { menuOpen = true },
            onLongClickLabel = stringResource(R.string.recent_calls_row_menu),
            leadingContent = {
                Avatar(
                    photo = call.photo,
                    name = name,
                    id = call.userId,
                    size = MaterialTheme.sizes.avatarRow,
                    onSelection = menuOpen,
                )
            },
            supportingContent = { CallKind(call) },
            trailingContent = { TimeAndType(call) },
            shapes = if (menuOpen) shapes.copy(shape = shapes.selectedShape) else shapes,
            colors = colors,
            contentPadding = insetContentPadding(insets),
            content = { RowText(name, MaterialTheme.typography.titleMedium.userContent) },
        )
        DeleteMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onDelete = {
                menuOpen = false
                onDelete()
            },
        )
    }
}

/** The direction's symbol, then how the call went and its length; in `error` for a missed call. */
@Composable
private fun CallKind(call: RecentCallsItem.Call) {
    val missed = call.outcome == CallOutcome.Missed
    val color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Icon(
            painter = painterResource(call.directionIcon()),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = color,
        )
        val kind = call.kind.resolve()
        RowText(
            text = call.duration?.let { stringResource(R.string.recent_calls_kind_and_length, kind, it) } ?: kind,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = color,
        )
    }
}

/** The call's time over the symbol of what a tap does: a voice or a video call. */
@Composable
private fun TimeAndType(call: RecentCallsItem.Call) {
    Column(horizontalAlignment = Alignment.End) {
        RowText(call.time, MaterialTheme.typography.labelSmall.userContent)
        Icon(
            painter = painterResource(if (call.isVideo) UiR.drawable.ic_videocam else UiR.drawable.ic_call),
            contentDescription = null,
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall).size(MaterialTheme.sizes.inlineIcon),
        )
    }
}

@DrawableRes
private fun RecentCallsItem.Call.directionIcon(): Int =
    when {
        outcome == CallOutcome.Missed -> R.drawable.ic_call_missed
        outcome == CallOutcome.Cancelled -> R.drawable.ic_call_missed_outgoing
        isOutgoing -> R.drawable.ic_call_made
        else -> R.drawable.ic_call_received
    }

/**
 * What screen readers hear of the row, what a tap does and its delete action, built only when something reads the
 * semantics tree.
 */
private fun Modifier.rowSemantics(
    call: RecentCallsItem.Call,
    name: String,
    resources: Resources,
    onClick: () -> Unit,
    onDelete: () -> Unit,
): Modifier =
    semantics {
        val back = if (call.isVideo) R.string.recent_calls_video_call_back else R.string.recent_calls_call_back
        onClick(label = resources.getString(back)) {
            onClick()
            true
        }
        val length = call.duration?.let { resources.getString(R.string.recent_calls_duration, it) }
        contentDescription = spokenList(resources, listOf(name, call.kind.resolve(resources), length, call.time))
        customActions =
            listOf(
                CustomAccessibilityAction(resources.getString(R.string.recent_calls_delete)) {
                    onDelete()
                    true
                },
            )
    }

/** One line of the row, which screen readers hear with the row. */
@Composable
private fun RowText(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics {},
        color = color,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = style,
    )
}

/** The row's menu: "Delete from call history" in `error`, an expressive [DropdownMenuPopup] of one group. */
@Composable
private fun DeleteMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            val error = MaterialTheme.colorScheme.error
            DropdownMenuItem(
                onClick = onDelete,
                text = { Text(stringResource(R.string.recent_calls_delete)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
            )
        }
    }
}

/** The list item's colors, or its selected ones while the row's menu is open. */
@Composable
private fun rowColors(selected: Boolean): ListItemColors {
    val colors = ListItemDefaults.colors()
    if (!selected) return colors
    return colors.copy(
        containerColor = colors.selectedContainerColor,
        contentColor = colors.selectedContentColor,
        leadingContentColor = colors.selectedLeadingContentColor,
        trailingContentColor = colors.selectedTrailingContentColor,
        supportingContentColor = colors.selectedSupportingContentColor,
    )
}

/**
 * How far a row's container sits in from its place in the list, as the chat list's: the small spacing step from the
 * sides of the sheet, and half of Material's gap between segmented list items from the rows around.
 */
@Composable
private fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
private fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

/** Times and lengths in figures of one width (`docs/DESIGN_SYSTEM.md` §3.2). */
private const val TABULAR_FIGURES = "tnum"
