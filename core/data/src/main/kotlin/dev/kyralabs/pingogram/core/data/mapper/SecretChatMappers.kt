package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import org.drinkless.tdlib.TdApi

internal fun TdApi.SecretChat.toSecretChat(): SecretChat =
    SecretChat(
        id = id,
        userId = userId,
        state = state.toSecretChatState(),
        isOutbound = isOutbound,
        keyHash = keyHash?.takeIf { it.isNotEmpty() }?.let(::EncryptionKeyHash) ?: EncryptionKeyHash.Empty,
        layer = layer,
    )

/** Null (td_api.tl requires a value, but the field is a Java reference) and states a newer TDLib adds: closed. */
internal fun TdApi.SecretChatState?.toSecretChatState(): SecretChatState =
    when (this) {
        is TdApi.SecretChatStatePending -> SecretChatState.Pending
        is TdApi.SecretChatStateReady -> SecretChatState.Ready
        else -> SecretChatState.Closed
    }
