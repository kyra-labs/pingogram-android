package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

// Locked chats (docs/adr/0029-locked-chats.md) reach the features that show chats through these, since the passcode's
// screens live in feature:settings, which no other feature depends on. `:app` provides all three.

/**
 * Keeps a locked chat behind the passcode: draws its `content` only while the chat is not hidden, and in its place
 * asks for the passcode or biometrics. The conversation wraps itself in it. In previews and tests, the content as is.
 */
val LocalLockedChatGate =
    staticCompositionLocalOf<@Composable (chatId: Long, content: @Composable () -> Unit) -> Unit> {
        { _, content -> content() }
    }

/** Opens the list of locked chats (Settings → Privacy and security → Locked chats); null where nothing provides it. */
val LocalLockedChatsLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Opens a chat from a screen of a feature that cannot depend on the conversation's; null where nothing provides it. */
val LocalChatLauncher = staticCompositionLocalOf<((chatId: Long) -> Unit)?> { null }
