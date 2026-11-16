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
import androidx.compose.foundation.lazy.LazyListScope
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
import dev.kyralabs.pingogram.core.ui.component.ItemMotion
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
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
 * what the user is in it, then for its members [CommunityActions]; then, for those who may change the list, the
 * requests to add a chat ([AddChatRequestRow]); then "Chats in this community" with their count over the chats as one
 * segmented group ([CommunityChatRow]) and a footer. "Community options" in the top bar opens an expressive menu, and
 * [CommunityAddChatsSheet] adds chats. Loading is a delayed indicator, a failure an [EmptyState] on `Cookie4Sided` with
 * "Try again".
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
    (state as? CommunityUiState.Content)?.let { content ->
        CommunityDialogs(content, onEvent)
        content.addChats?.let { CommunityAddChatsSheet(it, content.header.canEditChatList, onEvent) }
    }
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
    // Every item moves on the scheme's springs, so the headers and the footer move with the rows.
    val itemMotion = rememberItemMotion()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = remember(contentPadding, spacing) { contentPadding.plusBottom(spacing.extraLarge) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            CommunityHeaderBlock(state.header, rowModifier.animateItem(this, itemMotion))
        }
        if (state.header.isJoined) {
            item(key = ACTIONS, contentType = ACTIONS) {
                CommunityActions(
                    state.header,
                    onEvent,
                    rowModifier.animateItem(this, itemMotion).padding(bottom = spacing.medium),
                )
            }
        }
        requestItems(state.requests, numbers, rowModifier, itemMotion, onEvent)
        chatItems(state, numbers, rowModifier, itemMotion, onEvent)
    }
}

/** The chats under their count, then the footer. */
private fun LazyListScope.chatItems(
    state: CommunityUiState.Content,
    numbers: NumberFormat,
    rowModifier: Modifier,
    itemMotion: ItemMotion,
    onEvent: (CommunityEvent) -> Unit,
) {
    item(key = TITLE, contentType = TITLE) {
        ProfileSectionHeader(
            title =
                pluralStringResource(
                    R.plurals.community_chats,
                    state.chats.size,
                    numbers.format(state.chats.size),
                ),
            modifier = rowModifier.animateItem(this, itemMotion),
        )
    }
    itemsIndexed(state.chats, key = { _, chat -> chat.chatId }, contentType = { _, _ -> ROW }) { index, chat ->
        CommunityChatRow(
            chat = chat,
            shapes = ListItemDefaults.segmentedShapes(index, state.chats.size),
            onEvent = onEvent,
            modifier = rowModifier.animateItem(this, itemMotion),
            canRemove = state.header.canEditChatList,
        )
    }
    item(key = FOOTER, contentType = FOOTER) {
        Text(
            text =
                stringResource(
                    if (state.chats.isEmpty()) R.string.community_no_chats else R.string.community_footer,
                ),
            modifier =
                rowModifier
                    .animateItem(
                        this,
                        itemMotion,
                    ).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
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
 * "Community options": an expressive menu of showing the community as one entry in the chat list (for its members),
 * renaming and, in a group of its own in `error`, deleting.
 */
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
                listOfNotNull(
                    CommunityEvent.ToggleCollapsed.takeIf { header.isJoined },
                    CommunityEvent.Rename.takeIf { header.canRename },
                ).ifEmpty { null },
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
                            text = { Text(stringResource(optionText(event, header))) },
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

/** The text of [event] in the options menu. */
private fun optionText(
    event: CommunityEvent,
    header: CommunityHeader,
): Int =
    when (event) {
        CommunityEvent.Delete -> {
            R.string.community_delete
        }

        CommunityEvent.ToggleCollapsed -> {
            if (header.areChatsCollapsed) R.string.community_expand else R.string.community_collapse
        }

        else -> {
            R.string.community_rename
        }
    }

/** The dialog the page shows: renaming, or the question before deleting or removing a chat. */
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

        is CommunityDialog.RemoveChat -> {
            ConfirmationDialog(
                title = stringResource(R.string.community_remove_title, dialog.title.bidiIsolated()).bidiIsolated(),
                text = stringResource(R.string.community_remove_text),
                confirm = stringResource(R.string.community_remove_confirm),
                onConfirm = { onEvent(CommunityEvent.Confirm) },
                onDismiss = { onEvent(CommunityEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

private const val HEADER = "header"
private const val TITLE = "title"
private const val ACTIONS = "actions"
private const val ROW = "row"
private const val FOOTER = "footer"
