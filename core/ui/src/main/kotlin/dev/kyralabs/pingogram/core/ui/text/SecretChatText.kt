package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.ui.R

/**
 * What a secret chat that cannot be written to says instead of its last message or the other user's status
 * (`REQUIREMENTS.md` FR-E1), in the chat list and the conversation's header: "Waiting for Ada to come online" while
 * the user's own new chat waits for the other side ([name], isolated, `docs/DESIGN_SYSTEM.md` §9), "Setting up
 * encryption…" while TDLib accepts one the other user started, "Secret chat ended" once closed; null once it is ready.
 */
fun SecretChat.stateText(name: UiText): UiText? =
    when {
        state == SecretChatState.Ready -> null
        state == SecretChatState.Closed -> UiText.resource(R.string.secret_chat_ended)
        isOutbound -> UiText.resource(R.string.secret_chat_waiting, name.bidiIsolated())
        else -> UiText.resource(R.string.secret_chat_joining)
    }
