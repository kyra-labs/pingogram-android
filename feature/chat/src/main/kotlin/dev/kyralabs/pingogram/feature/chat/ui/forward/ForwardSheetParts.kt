package dev.kyralabs.pingogram.feature.chat.ui.forward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerField
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.core.ui.R as UiR

// The rows, options and send button of the forward sheet (ForwardBottomSheet).

/**
 * One chat: its avatar and name (a lock first for a secret chat) in a list item inset in the sheet, toggled by a tap.
 * Picked ([selected]), the row is a full pill in the list item's selected colors and its avatar morphs into a check.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ForwardTargetRow(
    target: ForwardTarget,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = target.title.resolve()
    val insets = rowInsets()
    val shapes = ListItemDefaults.shapes()
    ListItem(
        checked = selected,
        onCheckedChange = { onToggle() },
        modifier = modifier.padding(insets),
        leadingContent = {
            Avatar(
                photo = target.photo,
                name = title,
                id = target.chatId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = target.placeholderIcon,
                selected = selected,
            )
        },
        shapes = if (selected) shapes.copy(shape = CircleShape) else shapes,
        colors = listItemColors(selected),
        contentPadding = insetContentPadding(insets),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (target.isSecret) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_lock),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The forward options, then the comment field and the send button. */
@Composable
internal fun ForwardOptions(
    sheet: ForwardSheet,
    comment: ComposerText,
    onEvent: (ChatEvent) -> Unit,
    onSend: () -> Unit,
    onLink: (TextRange) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(top = spacing.small)) {
        if (sheet.canHideSenderName) {
            SwitchRow(
                title = stringResource(R.string.forward_hide_sender),
                checked = sheet.hideSenderName,
                onCheckedChange = { onEvent(ChatEvent.SetForwardHideSenderName(it)) },
            )
            if (sheet.hasCaptions) {
                SwitchRow(
                    title = stringResource(R.string.forward_hide_captions),
                    checked = sheet.hideCaptions,
                    onCheckedChange = { onEvent(ChatEvent.SetForwardHideCaptions(it)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.Bottom,
        ) {
            ComposerField(
                text = comment,
                onSend = onSend,
                onLink = onLink,
                onFocusChanged = {},
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.forward_comment),
            )
            SendButton(count = sheet.selectedIds.size, onClick = onSend)
        }
    }
}

/** Send: a medium Expressive button whose shape morphs when pressed; screen readers hear to how many chats. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendButton(
    count: Int,
    onClick: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val resources = LocalResources.current
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier =
            Modifier.heightIn(min = height).semantics {
                contentDescription = resources.getQuantityString(R.plurals.forward_send_description, count, count)
            },
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        Text(text = stringResource(R.string.forward_send), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/** A forward option as a list item with a [Switch], the whole row toggling it, on the sheet's color. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(title)
    }
}

/**
 * How far a row's container sits in from its place in the list, as in the chat list: [MaterialTheme.spacing]'s small
 * step from the sides of the sheet, and half of Material's gap between segmented list items from the rows around.
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

/** The list item's colors on the sheet's color, or its selected colors for a picked chat. */
@Composable
private fun listItemColors(selected: Boolean): ListItemColors {
    if (!selected) return ListItemDefaults.colors(containerColor = Color.Transparent)
    val colors = ListItemDefaults.colors()
    return colors.copy(
        containerColor = colors.selectedContainerColor,
        contentColor = colors.selectedContentColor,
        leadingContentColor = colors.selectedLeadingContentColor,
    )
}
