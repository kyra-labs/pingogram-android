package dev.kyralabs.pingogram.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * The Chats tab's list pane while the Channel feed may dock there (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame
 * 7b): the [feed] while [docked], else the chat [list]. System back closes the docked feed ([onClose]) while
 * [backEnabled], that is once nothing beside it (a conversation, a page) takes back first.
 */
@Composable
internal fun DockedFeedOrList(
    docked: Boolean,
    backEnabled: Boolean,
    onClose: () -> Unit,
    feed: @Composable () -> Unit,
    list: @Composable () -> Unit,
) {
    if (docked) {
        BackHandler(enabled = backEnabled, onBack = onClose)
        feed()
    } else {
        list()
    }
}
