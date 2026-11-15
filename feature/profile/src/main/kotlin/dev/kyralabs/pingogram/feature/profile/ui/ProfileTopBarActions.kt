package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.CallButtonMenu
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile's top bar actions (owner decisions 2026-11-15, board 24): "Call" once the bar collapsed past half way,
 * where the user can be called (not a bot, oneself or a deleted account; the voice and video menu where both are
 * possible, [CallButtonMenu]), fading in and out on the effects springs; then "More options" (⋮), an expressive
 * [DropdownMenuPopup] with "Secret chat", where the user may start one.
 */
@Composable
internal fun ProfileTopBarActions(
    actions: ProfileActions,
    collapsed: Boolean,
    onEvent: (ProfileEvent) -> Unit,
) {
    val calls = LocalCallStarter.current
    val callUserId = actions.callUserId
    val motion = MaterialTheme.motionScheme
    if (calls != null && callUserId != null) {
        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
            CallButtonMenu(userId = callUserId, calls = calls) { onClick, menu ->
                IconButton(onClick = onClick, modifier = menu, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_call),
                        contentDescription = stringResource(R.string.profile_action_call),
                    )
                }
            }
        }
    }
    if (actions.canStartSecretChat) MoreOptions(actions, onEvent)
}

/** "More options" (⋮) and its menu: "Secret chat", waiting while one is being started. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreOptions(
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.profile_more_options),
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onEvent(ProfileEvent.StartSecretChat)
                    },
                    text = { Text(stringResource(R.string.profile_action_secret_chat)) },
                    leadingIcon = { Icon(painterResource(UiR.drawable.ic_lock), contentDescription = null) },
                    enabled = !actions.isStartingSecretChat,
                    shape = MenuDefaults.itemShape(0, 1).shape,
                )
            }
        }
    }
}
