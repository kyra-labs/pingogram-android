package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The Chats tab's Focus as its header reads it: the [pill] while a profile is on (read by the header alone, so a change
 * recomposes nothing else) and what a tap on it does ([onOpen]). Provided by the Chats tab's route; the archive has
 * none.
 */
@Stable
internal class ChatListFocus(
    val pill: () -> FocusPill?,
    val onOpen: () -> Unit,
)

/** The Chats tab's [ChatListFocus], or null where the header shows no Focus (the archive, previews). */
internal val LocalChatListFocus = staticCompositionLocalOf<ChatListFocus?> { null }
