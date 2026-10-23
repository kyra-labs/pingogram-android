package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [ChatExportRepository] on TDLib: `getChat` (and `getUser` for a private chat's bot, `getMe` for Saved Messages),
 * `searchChatMessages` without a query or filter for the size of the history, `getChatHistory` with `only_local`
 * false (from the server where TDLib's database ends) a page of 100 at a time, `getUser` / `getChat` for names, and
 * `downloadFile` at priority 1, synchronous, one file at a time. Nothing is cached, so it is no `TdlibUpdateCache`.
 *
 * TDLib may return fewer messages than asked for, also before the end of the history; only an empty page ends it.
 * Messages at or newer than `from_message_id` are left out, so a page never repeats the previous one's oldest message.
 */
@Singleton
class TdlibChatExportRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChatExportRepository {
        override suspend fun chat(chatId: Long): Outcome<Pair<ExportChat, ChatExportAccess>> {
            val chat =
                when (val found = client.request(TdApi.GetChat(chatId))) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found
                }
            val kind =
                when (val type = chat.type) {
                    is TdApi.ChatTypePrivate -> privateKind(type.userId)
                    is TdApi.ChatTypeSecret -> ExportChatKind.Private
                    is TdApi.ChatTypeSupergroup -> if (type.isChannel) ExportChatKind.Channel else ExportChatKind.Group
                    else -> ExportChatKind.Group
                }
            val access =
                when {
                    chat.type is TdApi.ChatTypeSecret -> ChatExportAccess.SecretChat
                    chat.hasProtectedContent -> ChatExportAccess.ProtectedContent
                    else -> ChatExportAccess.Allowed
                }
            return Outcome.Success(ExportChat(chat.id, chat.title, kind) to access)
        }

        /** Saved Messages, a bot, or a person; a person also when the user or the account is not known. */
        private suspend fun privateKind(userId: Long): ExportChatKind {
            val me = (client.request(TdApi.GetMe()) as? Outcome.Success)?.value
            if (me?.id == userId) return ExportChatKind.SavedMessages
            val user = (client.request(TdApi.GetUser(userId)) as? Outcome.Success)?.value
            return if (user?.type is TdApi.UserTypeBot) ExportChatKind.Bot else ExportChatKind.Private
        }

        override suspend fun messageCount(chatId: Long): Outcome<Int?> =
            client
                .request(TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, 1, null))
                .map { found -> found.totalCount.takeIf { it > 0 } }

        override suspend fun page(
            chatId: Long,
            fromMessageId: Long,
            onFloodWait: suspend (seconds: Long) -> Unit,
        ): Outcome<List<Message>> =
            waitingOutFloods(onFloodWait) {
                client.request(
                    TdApi.GetChatHistory(chatId, fromMessageId, 0, ChatExportRepository.PAGE_SIZE, false),
                )
            }.map { messages ->
                messages.messages
                    .filterNotNull()
                    .filter { fromMessageId == 0L || it.id < fromMessageId }
                    .map { it.toMessage() }
                    .sortedByDescending { it.id }
            }

        override suspend fun senderName(sender: MessageSender): String? =
            when (sender) {
                is MessageSender.User -> {
                    (client.request(TdApi.GetUser(sender.userId)) as? Outcome.Success)?.value?.let { user ->
                        listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
                    }
                }

                is MessageSender.Chat -> {
                    (client.request(TdApi.GetChat(sender.chatId)) as? Outcome.Success)?.value?.title
                }
            }?.takeIf { it.isNotBlank() }

        override suspend fun download(
            fileId: Int,
            onFloodWait: suspend (seconds: Long) -> Unit,
        ): Outcome<String> {
            val downloaded =
                try {
                    waitingOutFloods(onFloodWait) {
                        client.request(TdApi.DownloadFile(fileId, PRIORITY, 0, 0, true), DOWNLOAD_TIMEOUT)
                    }
                } catch (cancelled: CancellationException) {
                    cancelDownload(fileId)
                    throw cancelled
                }
            // A download that ran out of time keeps running in TDLib unless it is cancelled.
            if (downloaded == Outcome.Failure(DomainError.Network)) cancelDownload(fileId)
            val local = (downloaded as? Outcome.Success)?.value?.local ?: return downloaded as Outcome.Failure
            val complete = local.isDownloadingCompleted && local.path.isNotEmpty()
            return if (complete) Outcome.Success(local.path) else Outcome.Failure(DomainError.NotFound)
        }

        /** Stops TDLib's download of [fileId] even while the caller is being cancelled; what it got stays. */
        private suspend fun cancelDownload(fileId: Int) {
            withContext(NonCancellable) { client.request(TdApi.CancelDownloadFile(fileId, false)) }
        }

        /**
         * [request], sent again after each `FLOOD_WAIT` once the seconds Telegram asked for passed (at least one), at
         * most [ChatExportRepository.MAX_FLOOD_WAITS] times; then its flood wait is the answer.
         */
        private suspend fun <T> waitingOutFloods(
            onFloodWait: suspend (seconds: Long) -> Unit,
            request: suspend () -> Outcome<T>,
        ): Outcome<T> {
            repeat(ChatExportRepository.MAX_FLOOD_WAITS) {
                val outcome = request()
                val wait = (outcome as? Outcome.Failure)?.error as? DomainError.FloodWait ?: return outcome
                val seconds = wait.seconds.coerceAtLeast(1)
                onFloodWait(seconds)
                delay(seconds.seconds)
            }
            return request()
        }

        private companion object {
            /** TDLib's lowest download priority: what the user looks at meanwhile goes first. */
            const val PRIORITY = 1

            /**
             * How long one file may take. TDLib waits for the network by itself; a file not done by then is cancelled
             * and fails the export with `DomainError.Network`, rather than holding it for ever.
             */
            val DOWNLOAD_TIMEOUT: Duration = 30.minutes
        }
    }
