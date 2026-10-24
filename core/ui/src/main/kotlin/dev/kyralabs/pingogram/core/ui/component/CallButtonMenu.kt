package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.R

/**
 * A call button that offers a voice and a video call (`docs/DESIGN_SYSTEM.md` §6, "Conversation top bar", "Call
 * buttons and notifications"): where [calls] says user [userId] can get a video call, a tap opens an expressive menu
 * anchored to the button ([DropdownMenuPopup], one group with "Voice call" and "Video call"); where only a voice call
 * is possible, a tap calls at once.
 *
 * [button] draws the button with the tap and a modifier to put on it, which gives screen readers "Voice call" and
 * "Video call" as custom actions while the menu is offered; its own label stays "Call".
 */
@Composable
fun CallButtonMenu(
    userId: Long,
    calls: CallStarter,
    modifier: Modifier = Modifier,
    button: @Composable (onClick: () -> Unit, modifier: Modifier) -> Unit,
) {
    val canVideoCall by remember(calls, userId) { calls.canVideoCall(userId) }.collectAsStateWithLifecycle(false)
    var expanded by rememberSaveable(userId) { mutableStateOf(false) }
    val voiceCall = stringResource(R.string.call_voice)
    val videoCall = stringResource(R.string.call_video)
    Box(modifier) {
        button(
            { if (canVideoCall) expanded = true else calls.call(userId) },
            if (canVideoCall) {
                Modifier.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(voiceCall) {
                                calls.call(userId)
                                true
                            },
                            CustomAccessibilityAction(videoCall) {
                                calls.videoCall(userId)
                                true
                            },
                        )
                }
            } else {
                Modifier
            },
        )
        DropdownMenuPopup(expanded = expanded && canVideoCall, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        calls.call(userId)
                    },
                    text = { Text(voiceCall) },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_call), contentDescription = null) },
                )
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        calls.videoCall(userId)
                    },
                    text = { Text(videoCall) },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.ic_videocam), contentDescription = null)
                    },
                )
            }
        }
    }
}
