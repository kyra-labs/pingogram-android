package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The conversation's top bar actions in the experimental look (`docs/DESIGN_SYSTEM.md` §13.5).

/**
 * The experimental look's actions for a group or channel (§13.5): a channel's mute toggle ([onSetMuted]) in search's
 * place, then "More options", a menu with search ([onSearch]) in a channel and the info page ([onOpenInfo]); a group
 * keeps search and adds the menu. Private and secret chats, forums' topic lists and topics keep [ChatActions].
 */
@Composable
internal fun ExperimentalGroupActions(
    header: ChatHeader,
    onSearch: (() -> Unit)?,
    onOpenInfo: (() -> Unit)?,
    onSetMuted: (Boolean) -> Unit,
) {
    if (header.isChannel) {
        MuteToggle(header.isMuted, onSetMuted)
    } else {
        onSearch?.let { search ->
            FilledTonalIconButton(onClick = search, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_search),
                    contentDescription = stringResource(R.string.search_open),
                )
            }
        }
    }
    MoreMenu(
        search = onSearch.takeIf { header.isChannel },
        info = onOpenInfo,
        infoLabel = if (header.isChannel) R.string.chat_channel_info else R.string.chat_group_info,
    )
}

/** A channel's notifications on and off, a filled tonal toggle with toggle haptics' semantics. */
@Composable
private fun MuteToggle(
    isMuted: Boolean,
    onSetMuted: (Boolean) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = !isMuted,
        onCheckedChange = { notify -> onSetMuted(!notify) },
        shapes = IconButtonDefaults.toggleableShapes(),
    ) {
        Icon(
            painter =
                painterResource(if (isMuted) UiR.drawable.ic_notifications_off else UiR.drawable.ic_notifications),
            contentDescription = stringResource(R.string.chat_notifications),
        )
    }
}

/** "More options": search in a channel ([search]) and the info page ([info]); nothing when it holds neither. */
@Composable
private fun MoreMenu(
    search: (() -> Unit)?,
    info: (() -> Unit)?,
    @StringRes infoLabel: Int,
) {
    if (search == null && info == null) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.chat_more_options),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                val items = listOfNotNull(search?.let { R.string.search_open to it }, info?.let { infoLabel to it })
                items.forEachIndexed { index, (label, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            expanded = false
                            action()
                        },
                        shape = MenuDefaults.itemShape(index, items.size).shape,
                        leadingIcon = {
                            Icon(
                                painter =
                                    painterResource(
                                        if (label ==
                                            R.string.search_open
                                        ) {
                                            UiR.drawable.ic_search
                                        } else {
                                            UiR.drawable.ic_info
                                        },
                                    ),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}
