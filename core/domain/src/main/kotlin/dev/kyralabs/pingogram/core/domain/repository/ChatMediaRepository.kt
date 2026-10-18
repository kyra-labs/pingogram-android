package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import kotlinx.coroutines.flow.Flow

/**
 * The photos, videos and GIFs of a chat, for the media viewer (`REQUIREMENTS.md` FR-C10): pages of the chat's media of
 * one [ChatMediaFilter] around a message and on either side of what was loaded, as TDLib finds them, from its database
 * where it can, else from the server. Nothing is kept: the viewer holds what it loaded, and [deletedMessages] tells it
 * what to drop.
 *
 * Failures are `DomainError.Network` and `DomainError.FloodWait` (try again), `DomainError.NotFound` (gone), and
 * others as TDLib reports them.
 */
interface ChatMediaRepository {
    /**
     * The page to open the viewer on message [messageId] of chat [chatId]: the filter of its kind (photos and videos
     * for a photo or video, GIFs for a GIF), the message, and up to about half a page of media on each side of it.
     * Fails with `DomainError.NotFound` if the message was deleted, or is not a photo, video or GIF the chat's media
     * hold (self-destructing media is not). A message TDLib's search does not find (one being sent) is still in the
     * page, in its place by id.
     */
    suspend fun around(
        chatId: Long,
        messageId: Long,
    ): Outcome<ChatMediaPage>

    /** The page of media of [filter] just older than message [messageId] of chat [chatId]. */
    suspend fun older(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<ChatMediaPage>

    /** The page of media of [filter] just newer than message [messageId] of chat [chatId]. */
    suspend fun newer(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<ChatMediaPage>

    /**
     * Where message [messageId] is among the media of [filter] of chat [chatId]: 1 for the newest, about as exact as
     * [ChatMediaPage.totalCount]. TDLib asks the server, so it fails in secret chats and for messages being sent.
     */
    suspend fun position(
        chatId: Long,
        filter: ChatMediaFilter,
        messageId: Long,
    ): Outcome<Int>

    /**
     * Whether the content of message [messageId] of chat [chatId] may leave Telegram: be saved to the device or shared
     * with other apps (`REQUIREMENTS.md` FR-C6, FR-C10). False where the chat protects its content
     * (`Chat.hasProtectedContent`), and where the sender or the current user protects the message, as TDLib says;
     * the media viewer offers Share and Save only when it is true.
     */
    suspend fun canBeSaved(
        chatId: Long,
        messageId: Long,
    ): Outcome<Boolean>

    /** The ids of the messages of chat [chatId] deleted for good, a batch at a time, for as long as it is collected. */
    fun deletedMessages(chatId: Long): Flow<Set<Long>>
}
