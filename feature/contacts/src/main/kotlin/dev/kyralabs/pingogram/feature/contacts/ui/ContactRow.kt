package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A contact in the list (`REQUIREMENTS.md` FR-P1, `docs/DESIGN_SYSTEM.md` §6), as the chat list's rows: an M3
 * [ListItem], Material's two-line item at 72 dp, with the 48 dp circular avatar and its online dot, the name the user
 * gave the contact in `titleMedium` and the status below it ("online", "last seen …"). Inset in its sheet like the chat
 * list's rows ([rowInsets]); pressed, and while its menu is open, it takes the list item's `large` selected shape in
 * `secondaryContainer`.
 *
 * A tap opens the chat ([onClick]); a long press (with the list item's `LongPress` haptic) opens a menu with Edit
 * ([onEdit]) and Delete ([onDelete], in `error`, in a group of its own). Screen readers hear the row as one ("Ada
 * Lovelace, online"), built only when something reads the semantics tree, with Edit and Delete as custom actions.
 */
@Composable
internal fun ContactRow(
    item: ContactItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val insets = rowInsets()
    val colors = listItemColors(selected = menuOpen)
    Box(modifier) {
        ListItem(
            onClick = onClick,
            modifier =
                Modifier
                    .padding(insets)
                    .rowSemantics(item, resources, onEdit, onDelete),
            onLongClick = { menuOpen = true },
            onLongClickLabel = stringResource(R.string.contacts_row_menu),
            leadingContent = {
                Avatar(
                    photo = item.photo,
                    name = item.name,
                    id = item.userId,
                    size = MaterialTheme.sizes.avatarRow,
                    isOnline = item.isOnline,
                    onlineRingColor = colors.containerColor,
                    onSelection = menuOpen,
                )
            },
            supportingContent = { RowText(item.status.resolve(), MaterialTheme.typography.bodyMedium) },
            shapes = listItemShapes(rounded = menuOpen),
            colors = colors,
            contentPadding = insetContentPadding(insets),
            trailingContent = if (item.fromPhone) ({ FromPhoneIcon() }) else null,
            content = { RowText(item.name, MaterialTheme.typography.titleMedium.userContent) },
        )
        ContactMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onEdit = {
                menuOpen = false
                onEdit()
            },
            onDelete = {
                menuOpen = false
                onDelete()
            },
        )
    }
}

/**
 * What screen readers hear of [item]'s row ("Ada Lovelace, online") and its Edit ([onEdit]) and Delete ([onDelete])
 * actions, built only when something reads the semantics tree.
 */
private fun Modifier.rowSemantics(
    item: ContactItem,
    resources: Resources,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
): Modifier =
    semantics {
        val fromPhone = if (item.fromPhone) ", ${resources.getString(R.string.contacts_from_phone)}" else ""
        contentDescription = "${item.name}, ${item.status.resolve(resources)}$fromPhone"
        customActions =
            listOf(
                CustomAccessibilityAction(resources.getString(R.string.contacts_edit)) {
                    onEdit()
                    true
                },
                CustomAccessibilityAction(resources.getString(R.string.contacts_delete)) {
                    onDelete()
                    true
                },
            )
    }

/** Contacts sync found the contact among the phone's contacts: the contacts icon, which the row's description says. */
@Composable
private fun FromPhoneIcon() {
    Icon(
        painter = painterResource(UiR.drawable.ic_contacts),
        contentDescription = null,
        modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One line of a row, in [style], which screen readers hear with the row. */
@Composable
private fun RowText(
    text: String,
    style: TextStyle,
) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics {},
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = style,
    )
}

/**
 * A row's menu, an expressive [DropdownMenuPopup]: Edit in one group, Delete in `error` in a second, as the message
 * actions keep destructive ones apart (`docs/DESIGN_SYSTEM.md` §6).
 */
@Composable
private fun ContactMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
            DropdownMenuItem(
                onClick = onEdit,
                text = { Text(stringResource(R.string.contacts_edit)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painter = painterResource(UiR.drawable.ic_edit), contentDescription = null) },
            )
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
            val error = MaterialTheme.colorScheme.error
            DropdownMenuItem(
                onClick = onDelete,
                text = { Text(stringResource(R.string.contacts_delete)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
            )
        }
    }
}

/**
 * An action above the contacts ("Add contact", "Invite friends"): its Material Symbol in a `primaryContainer` circle
 * the size of the contacts' avatars, so the rows line up, and its label in the list item's `bodyLarge`.
 */
@Composable
internal fun ContactsActionRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = rowInsets()
    ListItem(
        onClick = onClick,
        modifier = modifier.padding(insets),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(MaterialTheme.sizes.avatarRow)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors = listItemColors(selected = false),
        contentPadding = insetContentPadding(insets),
        content = { Text(label) },
    )
}

/**
 * How far a row's container sits in from its place in the list, as the chat list's: [MaterialTheme.spacing]'s small
 * step from the sides of the sheet, and half of Material's gap between segmented list items from the rows around.
 */
@Composable
internal fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
internal fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

/** The list item's colors, or its selected colors in place of its resting ones. */
@Composable
private fun listItemColors(selected: Boolean): ListItemColors {
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

/** The list item's shapes, with its selected shape (`large`) in place of its resting one while [rounded]. */
@Composable
private fun listItemShapes(rounded: Boolean): ListItemShapes {
    val shapes = ListItemDefaults.shapes()
    return if (rounded) shapes.copy(shape = shapes.selectedShape) else shapes
}

@PingogramPreviews
@Composable
private fun ContactRowPreview() {
    PingogramPreview {
        Column {
            ContactsActionRow(UiR.drawable.ic_add, "Add contact", onClick = {})
            ContactsPreviewData.contacts
                .take(
                    PREVIEW_ROWS,
                ).forEach { ContactRow(it, onClick = {}, onEdit = {}, onDelete = {}) }
        }
    }
}

private const val PREVIEW_ROWS = 3
