package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * What the parts of one conversation share, which [ChatViewModel] hands to the holders of its parts: the chat's id, the
 * saved state they keep across process death, the scope they run in, where their one-off effects go, the history, and
 * the lookup of a message in the history as it last came ([messageOf]).
 */
internal class ChatPartContext(
    val chatId: Long,
    val savedState: SavedStateHandle,
    val scope: CoroutineScope,
    val onEffect: (ChatEffect) -> Unit,
    val history: SharedFlow<ChatHistory>,
    val messageOf: (messageId: Long) -> Message?,
)
