package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender

/**
 * What a chat export reads from TDLib (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`): the chat, its history
 * a page at a time from the newest message back, the names of its senders, and its files, each downloaded once and
 * waited for. Only TDLib is asked, for what the user chose to export; nothing is cached between exports.
 *
 * A `FLOOD_WAIT` is waited out here, the seconds Telegram asks for and never less, before the request is sent again
 * ([onFloodWait] says so meanwhile); after [MAX_FLOOD_WAITS] in a row the call fails with `DomainError.FloodWait`.
 * Other failures are `DomainError.Network` (no answer in time), `DomainError.Unauthorized` (logged out meanwhile),
 * `DomainError.NotFound` (an unknown chat or file) and others as TDLib reports them. Cancelling a call stops its wait;
 * a download TDLib is running for it is cancelled too.
 */
interface ChatExportRepository {
    /** Chat [chatId] as an export names it, and whether it may be exported at all ([ChatExportAccess]). */
    suspend fun chat(chatId: Long): Outcome<Pair<ExportChat, ChatExportAccess>>

    /**
     * About how many messages chat [chatId] has, to tell how far an export of the whole history is; null where TDLib
     * cannot tell (td_api.tl's `getChatMessageCount` counts one kind of message only).
     */
    suspend fun messageCount(chatId: Long): Outcome<Int?>

    /**
     * Up to [PAGE_SIZE] messages of chat [chatId] older than [fromMessageId] (the newest ones for 0), newest first; an
     * empty page once the history's start is reached.
     */
    suspend fun page(
        chatId: Long,
        fromMessageId: Long,
        onFloodWait: suspend (seconds: Long) -> Unit = {},
    ): Outcome<List<Message>>

    /** The name of [sender] (a user's full name, a chat's title); null if TDLib does not know them. */
    suspend fun senderName(sender: MessageSender): String?

    /**
     * Downloads file [fileId] (at the lowest priority, one at a time, so what the user looks at goes first) and waits
     * until TDLib has all of it: its local path.
     */
    suspend fun download(
        fileId: Int,
        onFloodWait: suspend (seconds: Long) -> Unit = {},
    ): Outcome<String>

    companion object {
        /** Messages per page: the most `getChatHistory` returns at once. */
        const val PAGE_SIZE = 100

        /** Flood waits a call waits out in a row before it gives up. */
        const val MAX_FLOOD_WAITS = 3
    }
}

/** Whether a chat may be exported. */
enum class ChatExportAccess {
    Allowed,

    /** A secret chat: its messages are meant to stay on this device, in the chat, and cannot be forwarded either. */
    SecretChat,

    /** Its owner turned off saving and forwarding its content (`has_protected_content`), as Telegram's apps respect. */
    ProtectedContent,
}
