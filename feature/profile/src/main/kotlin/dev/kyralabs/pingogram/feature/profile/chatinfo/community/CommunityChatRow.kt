package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.ConfirmationDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat of the community: its avatar in its kind's shape, its title (user content), "Hidden · administrators only"
 * for a hidden one and what it is ("Group · 1,120 members"), and at its end "Joined" (a tonal label with a check) or
 * "Join" (a filled button, a [LoadingIndicator] in its place while Telegram answers). A tap opens the chat; where the
 * user may change the list ([canRemove]), a long press asks before removing it, which screen readers get as a custom
 * action. Screen readers hear the row as one ("Pingogram Beta, Group · 1,120 members, Joined"), and "Join" as a button
 * of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommunityChatRow(
    chat: CommunityChatItem,
    shapes: ListItemShapes,
    onEvent: (CommunityEvent) -> Unit,
    modifier: Modifier = Modifier,
    canRemove: Boolean = false,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val kind = chat.kindText(numbers)
    val hidden = stringResource(R.string.community_hidden)
    val joined = stringResource(R.string.community_joined)
    val remove = stringResource(R.string.community_remove_chat)
    SegmentedListItem(
        onClick = { onEvent(CommunityEvent.OpenChat(chat.chatId)) },
        shapes = shapes,
        colors = profileRowColors(),
        onLongClick = if (canRemove) ({ onEvent(CommunityEvent.RemoveChat(chat.chatId)) }) else null,
        onLongClickLabel = remove.takeIf { canRemove },
        modifier =
            modifier.rowSemantics(
                chat,
                listOf(
                    chat.title,
                    hidden.takeIf { chat.isHidden },
                    kind,
                    joined.takeIf {
                        chat.membership ==
                            Membership.Joined
                    },
                ),
                remove.takeIf { canRemove },
                onEvent,
            ),
        leadingContent = {
            Avatar(
                photo = chat.photo,
                name = chat.title,
                id = chat.chatId,
                size = MaterialTheme.sizes.avatarRow,
                shape = chat.shape,
            )
        },
        supportingContent = {
            // The hidden label and what the chat is wrap rather than cut each other off.
            FlowRow(
                modifier = Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (chat.isHidden) HiddenLabel(hidden)
                Text(kind, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = { MembershipAction(chat, onEvent, joined) },
    ) {
        Text(
            text = chat.title,
            modifier = Modifier.clearAndSetSemantics {},
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/**
 * The row as screen readers hear it: [parts] as one description, and removing the chat ([remove], null where the user
 * may not) as a custom action.
 */
@Composable
private fun Modifier.rowSemantics(
    chat: CommunityChatItem,
    parts: List<String?>,
    remove: String?,
    onEvent: (CommunityEvent) -> Unit,
): Modifier {
    val resources = LocalResources.current
    return semantics {
        if (remove != null) {
            customActions =
                listOf(
                    CustomAccessibilityAction(remove) {
                        onEvent(CommunityEvent.RemoveChat(chat.chatId))
                        true
                    },
                )
        }
        contentDescription = spokenList(resources, parts)
    }
}

/** "Hidden · administrators only" on a tonal pill, after the visibility-off symbol. */
@Composable
private fun HiddenLabel(text: String) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .background(colors.surfaceContainerHighest, CircleShape)
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_visibility_off),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = colors.onSurfaceVariant,
        )
        Text(
            text = text,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
    }
}

/** "Joined", "Join", or Telegram answering a join; nothing for a chat there is nothing to join. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MembershipAction(
    chat: CommunityChatItem,
    onEvent: (CommunityEvent) -> Unit,
    joined: String,
) {
    when (chat.membership) {
        Membership.Joined -> {
            val colors = MaterialTheme.colorScheme
            val spacing = MaterialTheme.spacing
            Row(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .background(colors.secondaryContainer, CircleShape)
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                    tint = colors.onSecondaryContainer,
                )
                Text(joined, color = colors.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
            }
        }

        Membership.CanJoin, Membership.Joining -> {
            val busy = chat.membership == Membership.Joining
            val label = stringResource(R.string.community_join_chat, chat.title.bidiIsolated())
            Button(
                onClick = { onEvent(CommunityEvent.Join(chat.chatId)) },
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineIcon))
                } else {
                    Text(stringResource(R.string.community_join), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }

        Membership.None -> {}
    }
}

/** What a chat of the community is, with its members or subscribers where known. */
@Composable
internal fun CommunityChatItem.kindText(numbers: NumberFormat): String {
    val count = memberCount
    return when (kind) {
        CommunityChatKind.Group -> {
            if (count == null) {
                stringResource(R.string.community_kind_group)
            } else {
                pluralStringResource(R.plurals.community_group_members, count, numbers.format(count))
            }
        }

        CommunityChatKind.Channel -> {
            if (count == null) {
                stringResource(R.string.community_kind_channel)
            } else {
                pluralStringResource(R.plurals.community_channel_subscribers, count, numbers.format(count))
            }
        }

        CommunityChatKind.Bot -> {
            stringResource(R.string.community_kind_bot)
        }

        CommunityChatKind.Chat -> {
            stringResource(R.string.community_kind_chat)
        }
    }
}
