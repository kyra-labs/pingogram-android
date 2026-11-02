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
 * is possible, a tap calls at once. In the demo the menu also offers "Receive a demo call", so the stores' reviewers
 * can answer one (`docs/adr/0034-demo-mode-for-store-review.md`), and so always opens.
 *
 * [button] draws the button with the tap and a modifier to put on it, which gives screen readers the menu's items as
 * custom actions while the menu is offered; its own label stays "Call".
 */
@Composable
fun CallButtonMenu(
    userId: Long,
    calls: CallStarter,
    modifier: Modifier = Modifier,
    button: @Composable (onClick: () -> Unit, modifier: Modifier) -> Unit,
) {
    val canVideoCall by remember(calls, userId) { calls.canVideoCall(userId) }.collectAsStateWithLifecycle(false)
    val canReceive = calls.canReceiveDemoCall
    var expanded by rememberSaveable(userId) { mutableStateOf(false) }
    val voiceCall = stringResource(R.string.call_voice)
    val videoCall = stringResource(R.string.call_video)
    val receiveCall = stringResource(R.string.call_receive_demo)
    val items =
        buildList {
            add(CallMenuItem(voiceCall, R.drawable.ic_call) { calls.call(userId) })
            if (canVideoCall) add(CallMenuItem(videoCall, R.drawable.ic_videocam) { calls.videoCall(userId) })
            if (canReceive) {
                add(
                    CallMenuItem(receiveCall, R.drawable.ic_call_incoming) { calls.receiveDemoCall(userId) },
                )
            }
        }
    val offersMenu = items.size > 1
    Box(modifier) {
        button(
            { if (offersMenu) expanded = true else calls.call(userId) },
            if (offersMenu) {
                Modifier.semantics {
                    customActions =
                        items.map { item ->
                            CustomAccessibilityAction(item.label) {
                                item.onClick()
                                true
                            }
                        }
                }
            } else {
                Modifier
            },
        )
        DropdownMenuPopup(expanded = expanded && offersMenu, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        text = { Text(item.label) },
                        shape = MenuDefaults.itemShape(index, items.size).shape,
                        leadingIcon = { Icon(painter = painterResource(item.icon), contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** One item of the call button's menu. */
private class CallMenuItem(
    val label: String,
    val icon: Int,
    val onClick: () -> Unit,
)
