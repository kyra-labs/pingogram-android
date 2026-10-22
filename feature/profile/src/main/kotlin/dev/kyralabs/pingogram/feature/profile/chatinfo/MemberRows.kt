package dev.kyralabs.pingogram.feature.profile.chatinfo

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors

/**
 * A member of a group or channel, a row of a segmented group ([shapes]), as Blocked users lists people: the 40 dp
 * circular avatar with its online dot, the name as user content, the status below it ("online" in `primary`), and the
 * role's label at the end in `labelMedium` ("Owner", "Admin", a tag, "Restricted", "Removed"). A tap opens the member
 * ([onClick]); where [onAction] is given and the member has actions, a long press opens an expressive
 * [DropdownMenuPopup] of them, removing in `error` in a group of its own, the row taking the list item's selected
 * colors while it is open. Screen readers hear the row as one ("Ada Lovelace, online, Admin"), with the actions as
 * custom actions, built only when something reads the semantics tree.
 */
@Composable
internal fun MemberRow(
    item: MemberItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: ((MemberAction) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val actions = if (onAction != null) item.actions else emptyList()
    val name = item.name.resolve()
    val colors = profileRowColors().let { if (menuOpen) it.asSelected() else it }
    Box(modifier) {
        SegmentedListItem(
            onClick = onClick,
            shapes = if (menuOpen) shapes.copy(shape = shapes.selectedShape) else shapes,
            modifier = Modifier.memberSemantics(item, name, resources, actions, onAction),
            onLongClick = if (actions.isNotEmpty()) ({ menuOpen = true }) else null,
            onLongClickLabel = stringResource(R.string.chat_info_member_menu).takeIf { actions.isNotEmpty() },
            colors = colors,
            leadingContent = {
                Avatar(
                    photo = item.photo,
                    name = name,
                    id = item.avatarId,
                    size = MaterialTheme.sizes.avatarSmall,
                    isOnline = item.isOnline,
                    onlineRingColor = colors.containerColor,
                    placeholderIcon = item.placeholderIcon,
                    onSelection = menuOpen,
                )
            },
            supportingContent =
                item.status?.let { status ->
                    {
                        val online = MaterialTheme.colorScheme.primary
                        MemberText(status.resolve(), color = if (item.isOnline) online else Color.Unspecified)
                    }
                },
            trailingContent =
                item.role?.let { role ->
                    { MemberText(role.resolve(), style = MaterialTheme.typography.labelMedium.userContent) }
                },
        ) {
            MemberText(name, style = MaterialTheme.typography.bodyLarge.userContent)
        }
        if (onAction != null) {
            MemberMenu(
                actions = actions,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { action ->
                    menuOpen = false
                    onAction(action)
                },
            )
        }
    }
}

/** One line of a member's row, which screen readers hear as part of the row's description, not on its own. */
@Composable
private fun MemberText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics {},
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/** The list item's selected colors in place of its resting ones, while the row's menu is open. */
private fun ListItemColors.asSelected(): ListItemColors =
    copy(
        containerColor = selectedContainerColor,
        contentColor = selectedContentColor,
        leadingContentColor = selectedLeadingContentColor,
        trailingContentColor = selectedTrailingContentColor,
        supportingContentColor = selectedSupportingContentColor,
    )

/** What screen readers hear of a member's row and its actions, built only when something reads the tree. */
private fun Modifier.memberSemantics(
    item: MemberItem,
    name: String,
    resources: Resources,
    actions: List<MemberAction>,
    onAction: ((MemberAction) -> Unit)?,
): Modifier =
    semantics {
        contentDescription =
            listOfNotNull(name, item.status?.resolve(resources), item.role?.resolve(resources)).joinToString(", ")
        if (onAction != null && actions.isNotEmpty()) {
            customActions =
                actions.map { action ->
                    CustomAccessibilityAction(resources.getString(action.label)) {
                        onAction(action)
                        true
                    }
                }
        }
    }

/** A member's actions, the everyday ones in one group and removing, in `error`, in a second. */
@Composable
private fun MemberMenu(
    actions: List<MemberAction>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (MemberAction) -> Unit,
) {
    val groups = actions.partition { it != MemberAction.Remove }.toList().filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        groups.forEachIndexed { groupIndex, group ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)) {
                group.forEachIndexed { index, action ->
                    val error = MaterialTheme.colorScheme.error
                    DropdownMenuItem(
                        onClick = { onAction(action) },
                        text = { Text(stringResource(action.label)) },
                        shape = MenuDefaults.itemShape(index, group.size).shape,
                        colors =
                            if (action == MemberAction.Remove) {
                                MenuDefaults.itemColors(textColor = error)
                            } else {
                                MenuDefaults.itemColors()
                            },
                    )
                }
            }
        }
    }
}

/** What an action is called in the menu and for screen readers. */
@get:StringRes
internal val MemberAction.label: Int
    get() =
        when (this) {
            MemberAction.Promote -> R.string.chat_info_member_promote
            MemberAction.EditRights -> R.string.chat_info_member_edit_rights
            MemberAction.Dismiss -> R.string.chat_info_member_dismiss
            MemberAction.Restrict -> R.string.chat_info_member_restrict
            MemberAction.Unrestrict -> R.string.chat_info_member_unrestrict
            MemberAction.Remove -> R.string.chat_info_member_remove
            MemberAction.Unban -> R.string.chat_info_member_unban
        }

/** The page's title for this filter: subscribers in a channel, members elsewhere. */
internal fun MemberFilter.titleFor(kind: GroupKind?): Int =
    when (this) {
        MemberFilter.Recent -> {
            if (kind == GroupKind.Channel) {
                R.string.chat_info_section_subscribers
            } else {
                R.string.chat_info_section_members
            }
        }

        MemberFilter.Administrators -> {
            R.string.chat_info_administrators
        }

        MemberFilter.Restricted -> {
            R.string.chat_info_restricted
        }

        MemberFilter.Banned -> {
            R.string.chat_info_removed
        }
    }

/** The chip's label for this filter. */
internal fun MemberFilter.chipFor(kind: GroupKind?): Int =
    when (this) {
        MemberFilter.Recent -> {
            if (kind == GroupKind.Channel) {
                R.string.chat_info_filter_subscribers
            } else {
                R.string.chat_info_filter_all
            }
        }

        MemberFilter.Administrators -> {
            R.string.chat_info_filter_admins
        }

        MemberFilter.Restricted -> {
            R.string.chat_info_filter_restricted
        }

        MemberFilter.Banned -> {
            R.string.chat_info_filter_removed
        }
    }
