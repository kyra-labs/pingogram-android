package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The link preview options of a text sent to a chat: none in a secret chat while the user has not allowed link
 * previews there ([LinkPreviewSettingsRepository], off by default), since Telegram's servers would make them; TDLib's
 * default elsewhere (null). Whether the chat is secret comes from `getChat`, which TDLib answers from memory for chats
 * it knows.
 */
@Singleton
class SecretChatLinkPreviews
    @Inject
    constructor(
        private val client: TdlibClient,
        private val settings: LinkPreviewSettingsRepository,
    ) {
        suspend fun optionsFor(chatId: Long): TdApi.LinkPreviewOptions? {
            val chat = (client.request(TdApi.GetChat(chatId)) as? Outcome.Success)?.value
            val withoutPreview = chat?.type is TdApi.ChatTypeSecret && !settings.secretChatPreviews.first()
            return if (withoutPreview) TdApi.LinkPreviewOptions(true, "", false, false, false) else null
        }
    }
