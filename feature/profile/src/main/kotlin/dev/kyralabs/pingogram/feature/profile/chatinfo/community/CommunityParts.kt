package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import kotlinx.collections.immutable.ImmutableList
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a member does with the community (the owner's board 19a), under its header: "Add chats" ("Suggest a chat"
 * where they may not change the list), a filled button with the add symbol, and "Mute" / "Unmute", a tonal one,
 * connected, sharing the row's width (`ButtonGroupDefaults` connected shapes and their press morph). The board's
 * "Invite" is left out: TDLib has no invite link for a community.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommunityActions(
    header: CommunityHeader,
    onEvent: (CommunityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        Button(
            onClick = { onEvent(CommunityEvent.AddChats) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes().asButtonShapes(),
            modifier = Modifier.weight(WEIGHT_ADD),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                text =
                    stringResource(
                        if (header.canEditChatList) R.string.community_add_chats else R.string.community_suggest_chat,
                    ),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        val mute =
            stringResource(
                if (header.isMuted) R.string.community_unmute_description else R.string.community_mute_description,
            )
        FilledTonalButton(
            onClick = { onEvent(CommunityEvent.ToggleMute) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes().asButtonShapes(),
            modifier = Modifier.weight(1f).semantics { contentDescription = mute },
        ) {
            Text(
                text = stringResource(if (header.isMuted) R.string.community_unmute else R.string.community_mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A request to add a chat, for those who may change the list: the chat's avatar in its kind's shape, its title (user
 * content), "Suggested by Ada" and "Hidden · admins only" where it would be, and at its end "Add" (a tonal button) and
 * "Decline" (an `IconButton` with the close symbol), both named with the chat for screen readers, a
 * [LoadingIndicator] in their place while Telegram takes the answer. A tap opens the chat.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddChatRequestRow(
    request: AddChatRequestItem,
    shapes: ListItemShapes,
    onEvent: (CommunityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val chat = request.chat
    val from = stringResource(R.string.community_request_from, request.senderName.resolve().bidiIsolated())
    val hidden = stringResource(R.string.community_hidden)
    SegmentedListItem(
        onClick = { onEvent(CommunityEvent.OpenChat(chat.chatId)) },
        shapes = shapes,
        colors = profileRowColors(),
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = spokenList(resources, listOf(chat.title, hidden.takeIf { chat.isHidden }, from))
            },
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
            Text(
                text = if (chat.isHidden) "$from · $hidden" else from,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { RequestActions(request, onEvent) },
    ) {
        Text(
            text = chat.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/** "Add" and "Decline", or Telegram answering. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RequestActions(
    request: AddChatRequestItem,
    onEvent: (CommunityEvent) -> Unit,
) {
    if (request.isBusy) {
        LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineIcon))
        return
    }
    val title = request.chat.title.bidiIsolated()
    val approve = stringResource(R.string.community_request_approve_chat, title)
    val decline = stringResource(R.string.community_request_decline_chat, title)
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = { onEvent(CommunityEvent.AnswerRequest(request.chat.chatId, approve = true)) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.semantics { contentDescription = approve },
        ) {
            Text(stringResource(R.string.community_request_approve))
        }
        IconButton(
            onClick = { onEvent(CommunityEvent.AnswerRequest(request.chat.chatId, approve = false)) },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painter = painterResource(UiR.drawable.ic_close), contentDescription = decline)
        }
    }
}

/**
 * The requests to add a chat, for those who may change the list: "2 requests to add a chat" over the requests as one
 * segmented group ([AddChatRequestRow]); nothing while none wait.
 */
internal fun LazyListScope.requestItems(
    requests: ImmutableList<AddChatRequestItem>,
    numbers: NumberFormat,
    rowModifier: Modifier,
    onEvent: (CommunityEvent) -> Unit,
) {
    if (requests.isEmpty()) return
    item(key = REQUESTS, contentType = REQUESTS) {
        ProfileSectionHeader(
            title = pluralStringResource(R.plurals.community_requests, requests.size, numbers.format(requests.size)),
            modifier = rowModifier,
        )
    }
    itemsIndexed(
        requests,
        key = { _, request -> "request-${request.chat.chatId}" },
        contentType = { _, _ -> REQUEST },
    ) { index, request ->
        AddChatRequestRow(
            request = request,
            shapes = ListItemDefaults.segmentedShapes(index, requests.size),
            onEvent = onEvent,
            modifier = rowModifier.animateItem(),
        )
    }
}

/** A connected button's shapes, for a [Button] rather than a toggle button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ToggleButtonShapes.asButtonShapes(): ButtonShapes = ButtonShapes(shape = shape, pressedShape = pressedShape)

/** The share of the row "Add chats" takes beside "Mute", as on the board. */
private const val WEIGHT_ADD = 1.3f

private const val REQUESTS = "requests"
private const val REQUEST = "request"
