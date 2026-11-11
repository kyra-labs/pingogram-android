package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A community's entry in the main list (the owner's board 19b, `docs/DESIGN_SYSTEM.md` §6 "Community entry"), the
 * first segment of its group on `surfaceContainer`, as the pinned chats' group: its photo on `MaterialShapes.Sunny`,
 * its name over "Community · 3 chats you joined" ("Community · 4 chats · muted"), the unread messages of its chats
 * in a badge (muted: `surfaceVariant`) and a chevron in a tonal circle that turns half a turn as it expands. A tap
 * shows its chats below it or hides them ([ChatListEvent.ToggleCommunity]). A long press opens an expressive menu
 * with "Show chats separately" ([CommunityEntryMenu]), which screen readers get as a custom action. Disabled in
 * selection mode ([enabled]).
 *
 * For screen readers it is one button: "Kyra Labs Community, Community · 3 chats you joined, 5 unread messages",
 * "Expanded" or "Collapsed".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommunityEntryRow(
    entry: CommunityEntry,
    enabled: Boolean,
    onEvent: (ChatListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val chats = entry.chats.size
    val summary = communitySummary(entry)
    val unread = entry.unreadCount.takeIf { it > 0 }?.let { resources.getQuantityString(UNREAD, it, it) }
    val state =
        stringResource(
            if (entry.isExpanded) R.string.chat_list_community_expanded else R.string.chat_list_community_collapsed,
        )
    val separately = stringResource(R.string.chat_list_community_separately)
    val showSeparately = { onEvent(ChatListEvent.ShowCommunityChatsSeparately(entry.id)) }
    var menuOpen by remember { mutableStateOf(false) }
    val insets = rowInsets()
    Box(modifier) {
        ListItem(
            onClick = { onEvent(ChatListEvent.ToggleCommunity(entry.id)) },
            modifier =
                Modifier.padding(insets).semantics {
                    contentDescription = spokenList(resources, listOf(entry.name, summary, unread))
                    stateDescription = state
                    if (enabled) {
                        customActions =
                            listOf(CustomAccessibilityAction(separately) { showSeparately().let { true } })
                    }
                },
            enabled = enabled,
            onLongClick = if (enabled) ({ menuOpen = true }) else null,
            onLongClickLabel = if (enabled) stringResource(R.string.chat_list_community_menu) else null,
            shapes =
                listItemShapes(
                    rounded = menuOpen,
                    segment = RowSegment(0, if (entry.isExpanded) chats + 1 else 1),
                ),
            colors = listItemColors(selected = menuOpen, grouped = true),
            contentPadding = insetContentPadding(insets),
            leadingContent = { CommunityAvatar(entry) },
            supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { CommunityEntryEnd(entry) },
        ) {
            val typography = MaterialTheme.typography
            val style = if (entry.unreadCount > 0) typography.titleMediumEmphasized else typography.titleMedium
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style.userContent)
        }
        CommunityEntryMenu(
            expanded = menuOpen,
            label = separately,
            onDismiss = { menuOpen = false },
            onSeparately = {
                menuOpen = false
                showSeparately()
            },
        )
    }
}

/** The community's photo, or its initials, on `MaterialShapes.Sunny`. */
@Composable
private fun CommunityAvatar(entry: CommunityEntry) {
    Avatar(
        photo = entry.photo,
        name = entry.name,
        id = entry.id,
        size = MaterialTheme.sizes.avatarRow,
        shape = AvatarShape.Community,
    )
}

/** "Community · 3 chats you joined", or "Community · 4 chats · muted" for a muted community. */
@Composable
private fun communitySummary(entry: CommunityEntry): String {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val chats = entry.chats.size
    return pluralStringResource(
        if (entry.isMuted) R.plurals.chat_list_community_chats_muted else R.plurals.chat_list_community_chats,
        chats,
        numbers.format(chats),
    )
}

/** The entry's menu: "Show chats separately" alone, in one expressive group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommunityEntryMenu(
    expanded: Boolean,
    label: String,
    onDismiss: () -> Unit,
    onSeparately: () -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
            DropdownMenuItem(
                text = { Text(label) },
                onClick = onSeparately,
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_unfold_more), contentDescription = null) },
            )
        }
    }
}

/** The badge of the community's unread messages, then the chevron in its tonal circle. */
@Composable
private fun CommunityEntryEnd(entry: CommunityEntry) {
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(
        targetValue = if (entry.isExpanded) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "communityChevron",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.unreadCount > 0) {
            Badge(
                containerColor = if (entry.isMuted) colors.surfaceVariant else colors.primary,
                contentColor = if (entry.isMuted) colors.onSurfaceVariant else colors.onPrimary,
            ) { Text(unreadCountText(entry.unreadCount)) }
        }
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarExtraSmall)
                    .background(colors.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon).graphicsLayer { rotationZ = turn },
                tint = colors.onSecondaryContainer,
            )
        }
    }
}

private val UNREAD = R.plurals.chat_list_a11y_unread
private const val HALF_TURN = 180f
