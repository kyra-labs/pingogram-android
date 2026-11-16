package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * The Chats tab's Focus as its header reads it: the [pill] while a profile is on (read by the header alone, so a change
 * recomposes nothing else) and what a tap on it does ([onOpen]). Provided by the Chats tab's route; the archive has
 * none. The pill's measured height ([pillHeight], snapshot state) gives it room in the header: two lines at large
 * font scales (board 31).
 */
@Stable
internal class ChatListFocus(
    val pill: () -> FocusPill?,
    val onOpen: () -> Unit,
) {
    var pillHeight: Dp by mutableStateOf(Dp.Unspecified)
}

/** The Chats tab's [ChatListFocus], or null where the header shows no Focus (the archive, previews). */
internal val LocalChatListFocus = staticCompositionLocalOf<ChatListFocus?> { null }
