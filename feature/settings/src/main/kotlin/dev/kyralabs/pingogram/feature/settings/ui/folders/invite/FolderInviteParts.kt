package dev.kyralabs.pingogram.feature.settings.ui.folders.invite

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList

// The parts of the shared folder sheet (FolderInviteSheet): its chat lists and its actions.

/**
 * Chats of the shared folder under [title] (`titleSmallEmphasized` in `primary`): with [onToggle], toggleable rows
 * inset like the chat list's, a picked one `large` in `secondaryContainer` with its avatar turned into a check (§4);
 * without, plain rows.
 */
@Composable
internal fun FolderInviteChats(
    title: String,
    chats: ImmutableList<FolderInviteChat>,
    onToggle: ((Long) -> Unit)?,
    enabled: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier =
                Modifier
                    .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                    .semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        chats.forEach { chat -> FolderInviteChatRow(chat, onToggle, enabled) }
    }
}

@Composable
private fun FolderInviteChatRow(
    chat: FolderInviteChat,
    onToggle: ((Long) -> Unit)?,
    enabled: Boolean,
) {
    val picked = onToggle != null && chat.picked
    val shape = if (picked) MaterialTheme.shapes.large else MaterialTheme.shapes.extraSmall
    val container = if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val row =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small)
            .clip(shape)
            .background(container)
    ListItem(
        modifier =
            if (onToggle != null) {
                row.toggleable(value = chat.picked, enabled = enabled, role = Role.Checkbox) { onToggle(chat.chatId) }
            } else {
                row
            },
        leadingContent = {
            Avatar(
                photo = chat.photo,
                name = chat.title,
                id = chat.chatId,
                size = MaterialTheme.sizes.avatarRow,
                selected = picked,
                onSelection = picked,
            )
        },
        supportingContent = {
            Text(stringResource(if (chat.channel) R.string.folder_invite_channel else R.string.folder_invite_group))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(chat.title, style = LocalTextStyle.current.userContent, maxLines = 1)
    }
}

/**
 * The sheet's actions, as the app's flows place them: [primary] a default filled `Button` at the end (none where it is
 * null), [secondary] a `TextButton` at the start; they wrap below each other where both do not fit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderInviteActions(
    @StringRes primary: Int?,
    enabled: Boolean,
    onPrimary: () -> Unit,
    @StringRes secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(secondary), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box {}
        }
        if (primary != null) {
            Button(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(primary), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}
