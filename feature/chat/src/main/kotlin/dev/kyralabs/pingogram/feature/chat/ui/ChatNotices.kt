package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.voice.voiceNoticeActionLabel

/**
 * Shows [message], the snackbar of [effect]; after forwarding to one other chat ([ChatEffect.Forwarded.openChatId])
 * with an action that opens it through [onOpenChat]; for the voice recording's microphone permission with an action
 * ([voiceNoticeActionLabel]) that [onVoiceAction] carries out, shown longer. Returns once the snackbar is gone.
 */
internal suspend fun SnackbarHostState.showNotice(
    message: String,
    effect: ChatEffect,
    resources: Resources,
    onOpenChat: (chatId: Long) -> Unit,
    onVoiceAction: (VoiceEffect) -> Unit = {},
) {
    val voiceAction = (effect as? VoiceEffect)?.let(::voiceNoticeActionLabel)
    if (effect is VoiceEffect && voiceAction != null) {
        val result = showSnackbar(message, resources.getString(voiceAction), duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onVoiceAction(effect)
        return
    }
    val openChatId = (effect as? ChatEffect.Forwarded)?.openChatId
    if (openChatId == null) {
        showSnackbar(message)
        return
    }
    val result = showSnackbar(message, resources.getString(R.string.forward_open_chat))
    if (result == SnackbarResult.ActionPerformed) onOpenChat(openChatId)
}

/**
 * The snackbar after forwarding ([ChatEffect.Forwarded], [ChatEffect.ForwardFailed]): "Forwarded to Ada" for one
 * chat whose name the sheet showed, else "Forwarded to 3 chats"; or why it failed, with "Not forwarded to 2 chats."
 * first where other chats got the messages. Empty for other effects.
 */
internal fun forwardNotice(
    resources: Resources,
    effect: ChatEffect,
    showErrorDetails: Boolean,
): String =
    when (effect) {
        is ChatEffect.Forwarded -> {
            val title = effect.chatTitle
            if (title != null) {
                resources.getString(R.string.forward_done_to, title.resolve(resources))
            } else {
                resources.getQuantityString(R.plurals.forward_done, effect.count, effect.count)
            }
        }

        is ChatEffect.ForwardFailed -> {
            val reason = errorMessage(resources, effect.error, showErrorDetails)
            if (effect.forwarded == 0) {
                reason
            } else {
                resources.getQuantityString(R.plurals.forward_failed_some, effect.failed, effect.failed, reason)
            }
        }

        else -> {
            ""
        }
    }
