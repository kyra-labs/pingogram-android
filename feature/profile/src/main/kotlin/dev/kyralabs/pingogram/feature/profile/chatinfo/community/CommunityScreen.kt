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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
 * The page of a community (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a): a settings-style page
 * ([InfoPageScaffold], "Community") whose header shows the community's photo on `MaterialShapes.Sunny`, its name and
 * what the user is in it; then "Chats in this community" with their count over the chats as one segmented group
 * ([CommunityChatRow]) and a footer. "Community options" in the top bar (where the user may rename or delete it) opens
 * an expressive menu. Loading is a delayed indicator, a failure an [EmptyState] on `Cookie4Sided` with "Try again".
 */
@Composable
internal fun CommunityScreen(
    state: CommunityUiState,
    onEvent: (CommunityEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val header = (state as? CommunityUiState.Content)?.header
    InfoPageScaffold(
        title = stringResource(R.string.community_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = { if (header?.hasOptions == true) CommunityOptions(header, onEvent) },
    ) { innerPadding ->
        InfoPageBody(innerPadding) { contentPadding ->
            when (state) {
                CommunityUiState.Loading -> {
                    val description = stringResource(R.string.community_loading)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                is CommunityUiState.Error -> {
                    CommunityError(state, onEvent)
                }

                is CommunityUiState.Content -> {
                    CommunityContent(state, onEvent, contentPadding)
                }
            }
        }
    }
    (state as? CommunityUiState.Content)?.let { CommunityDialogs(it, onEvent) }
}

/** Why the community could not be read, with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommunityError(
    state: CommunityUiState.Error,
    onEvent: (CommunityEvent) -> Unit,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.community_error_title),
        body = errorText(state.error),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = { onEvent(CommunityEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** The header, then the chats under their count and the footer, as wide as a column gets. */
@Composable
private fun CommunityContent(
    state: CommunityUiState.Content,
    onEvent: (CommunityEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val spacing = MaterialTheme.spacing
    val rowModifier =
        Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = windowMargin())
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = remember(contentPadding, spacing) { contentPadding.plusBottom(spacing.extraLarge) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) { CommunityHeaderBlock(state.header, rowModifier) }
        item(key = TITLE, contentType = TITLE) {
            ProfileSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.community_chats,
                        state.chats.size,
                        numbers.format(state.chats.size),
                    ),
                modifier = rowModifier,
            )
        }
        itemsIndexed(state.chats, key = { _, chat -> chat.chatId }, contentType = { _, _ -> ROW }) { index, chat ->
            CommunityChatRow(
                chat = chat,
                shapes = ListItemDefaults.segmentedShapes(index, state.chats.size),
                onEvent = onEvent,
                modifier = rowModifier.animateItem(),
            )
        }
        item(key = FOOTER, contentType = FOOTER) {
            Text(
                text =
                    stringResource(
                        if (state.chats.isEmpty()) R.string.community_no_chats else R.string.community_footer,
                    ),
                modifier = rowModifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The community's photo on `Sunny`, its name and what the user is in it, centred; one heading. */
@Composable
private fun CommunityHeaderBlock(
    header: CommunityHeader,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val name = header.name.ifEmpty { stringResource(R.string.community_title) }
    val kind =
        when (header.role) {
            CommunityRoleLabel.Owner -> stringResource(R.string.community_role_owner)
            CommunityRoleLabel.Administrator -> stringResource(R.string.community_role_admin)
            null -> stringResource(R.string.community_kind)
        }
    Column(
        modifier = modifier.padding(vertical = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Avatar(
            photo = header.photo,
            name = name,
            id = header.id,
            size = MaterialTheme.sizes.avatarLarge,
            shape = AvatarShape.Community,
        )
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmallEmphasized.userContent,
            )
            Text(
                text = kind,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A chat of the community: its avatar in its kind's shape, its title (user content), "Hidden · administrators only"
 * for a hidden one and what it is ("Group · 1,120 members"), and at its end "Joined" (a tonal label with a check) or
 * "Join" (a filled button, a [LoadingIndicator] in its place while Telegram answers). A tap opens the chat. Screen
 * readers hear the row as one ("Pingogram Beta, Group · 1,120 members, Joined"), and "Join" as a button of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommunityChatRow(
    chat: CommunityChatItem,
    shapes: ListItemShapes,
    onEvent: (CommunityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val kind = chat.kindText(numbers)
    val hidden = stringResource(R.string.community_hidden)
    val joined = stringResource(R.string.community_joined)
    SegmentedListItem(
        onClick = { onEvent(CommunityEvent.OpenChat(chat.chatId)) },
        shapes = shapes,
        colors = profileRowColors(),
        modifier =
            modifier.semantics {
                contentDescription =
                    spokenList(
                        resources,
                        listOf(
                            chat.title,
                            hidden.takeIf { chat.isHidden },
                            kind,
                            joined.takeIf { chat.membership == Membership.Joined },
                        ),
                    )
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

/** "Community options": an expressive menu of renaming and, in a group of its own in `error`, deleting. */
@Composable
private fun CommunityOptions(
    header: CommunityHeader,
    onEvent: (CommunityEvent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_settings),
                contentDescription = stringResource(R.string.community_options),
            )
        }
        val groups =
            listOfNotNull(
                listOf(CommunityEvent.Rename).takeIf { header.canRename },
                listOf(CommunityEvent.Delete).takeIf { header.canDelete },
            )
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            groups.forEachIndexed { groupIndex, group ->
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)) {
                    group.forEachIndexed { index, event ->
                        val isDelete = event == CommunityEvent.Delete
                        DropdownMenuItem(
                            onClick = {
                                open = false
                                onEvent(event)
                            },
                            text = {
                                Text(
                                    stringResource(
                                        if (isDelete) R.string.community_delete else R.string.community_rename,
                                    ),
                                )
                            },
                            shape = MenuDefaults.itemShape(index, group.size).shape,
                            colors =
                                if (isDelete) {
                                    MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                                } else {
                                    MenuDefaults.itemColors()
                                },
                        )
                    }
                }
            }
        }
    }
}

/** The dialog the page shows: renaming, or the question before deleting. */
@Composable
private fun CommunityDialogs(
    state: CommunityUiState.Content,
    onEvent: (CommunityEvent) -> Unit,
) {
    when (val dialog = state.dialog) {
        is CommunityDialog.Rename -> {
            RenameCommunityDialog(dialog.name, state.isBusy, onEvent)
        }

        CommunityDialog.Delete -> {
            val name = state.header.name.bidiIsolated()
            ConfirmationDialog(
                title = stringResource(R.string.community_delete_title, name).bidiIsolated(),
                text = stringResource(R.string.community_delete_text),
                confirm = stringResource(R.string.community_delete_confirm),
                onConfirm = { onEvent(CommunityEvent.Confirm) },
                onDismiss = { onEvent(CommunityEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

/** What a chat of the community is, with its members or subscribers where known. */
@Composable
private fun CommunityChatItem.kindText(numbers: NumberFormat): String {
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

private const val HEADER = "header"
private const val TITLE = "title"
private const val ROW = "row"
private const val FOOTER = "footer"
