package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.SharedMediaPage
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SharedMediaRepository] on TDLib's `searchChatMessages` with the search filter of each [SharedMediaKind]
 * (`searchMessagesFilterPhotoAndVideo`, `…Document`, `…Url`, `…VoiceNote`, `…Audio`, `…Animation`). TDLib answers from
 * its message database where that holds the chat's messages of the kind (always in secret chats, which the server
 * cannot search), else from the server; it pages by `next_from_message_id`, which is 0 after the last page. Nothing is
 * cached, so it is no `TdlibUpdateCache`.
 *
 * Self-destructing media is left out, as TDLib does; so is anything TDLib might mix in that is not of the kind, which
 * the tabs could not show.
 */
@Singleton
class TdlibSharedMediaRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SharedMediaRepository {
        override suspend fun page(
            chatId: Long,
            kind: SharedMediaKind,
            fromMessageId: Long,
            limit: Int,
        ): Outcome<SharedMediaPage> =
            client
                .request(TdApi.SearchChatMessages(chatId, null, "", null, fromMessageId, 0, limit, kind.filter()))
                .map { found ->
                    SharedMediaPage(
                        messages =
                            found.messages
                                .filterNotNull()
                                .map { it.toMessage() }
                                .filter { it.isOf(kind) }
                                .sortedByDescending { it.id }
                                .toImmutableList(),
                        nextFromMessageId = found.nextFromMessageId,
                        totalCount = found.totalCount.takeIf { it >= 0 },
                    )
                }
    }

private fun SharedMediaKind.filter(): TdApi.SearchMessagesFilter =
    when (this) {
        SharedMediaKind.PhotosAndVideos -> TdApi.SearchMessagesFilterPhotoAndVideo()
        SharedMediaKind.Files -> TdApi.SearchMessagesFilterDocument()
        SharedMediaKind.Links -> TdApi.SearchMessagesFilterUrl()
        SharedMediaKind.Voice -> TdApi.SearchMessagesFilterVoiceNote()
        SharedMediaKind.Music -> TdApi.SearchMessagesFilterAudio()
        SharedMediaKind.Gifs -> TdApi.SearchMessagesFilterAnimation()
    }

/**
 * Whether the message is one the tab of [kind] shows: links can be in any message with a text or caption, which the
 * tab reads them from; the other kinds go by the content, without self-destructing media.
 */
private fun Message.isOf(kind: SharedMediaKind): Boolean {
    if (kind == SharedMediaKind.Links) return true
    return when (val content = content) {
        is MessageContent.Photo -> kind == SharedMediaKind.PhotosAndVideos && !content.isSecret
        is MessageContent.Video -> kind == SharedMediaKind.PhotosAndVideos && !content.isSecret
        is MessageContent.Animation -> kind == SharedMediaKind.Gifs && !content.isSecret
        is MessageContent.Document -> kind == SharedMediaKind.Files
        is MessageContent.VoiceNote -> kind == SharedMediaKind.Voice
        is MessageContent.Audio -> kind == SharedMediaKind.Music
        else -> false
    }
}
