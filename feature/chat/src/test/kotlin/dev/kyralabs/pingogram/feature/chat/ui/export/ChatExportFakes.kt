package dev.kyralabs.pingogram.feature.chat.ui.export

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatExportJob
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportJobs
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The chat the export page reads: [chat] and [access], or [error]. */
internal class FakeChatExportRepository : ChatExportRepository {
    var chat = ExportChat(100, "Ada Lovelace", ExportChatKind.Private)
    var access = ChatExportAccess.Allowed
    var error: DomainError? = null
    var chatRequests = 0

    override suspend fun chat(chatId: Long): Outcome<Pair<ExportChat, ChatExportAccess>> {
        chatRequests++
        return error?.let { Outcome.Failure(it) } ?: Outcome.Success(chat to access)
    }

    override suspend fun messageCount(chatId: Long): Outcome<Int?> = Outcome.Success(null)

    override suspend fun page(
        chatId: Long,
        fromMessageId: Long,
        onFloodWait: suspend (seconds: Long) -> Unit,
    ): Outcome<List<Message>> = Outcome.Success(emptyList())

    override suspend fun senderName(sender: MessageSender): String? = null

    override suspend fun download(
        fileId: Int,
        onFloodWait: suspend (seconds: Long) -> Unit,
    ): Outcome<String> = Outcome.Failure(DomainError.NotFound)
}

/** WorkManager's side: [job] is what it reports; [started] and [cancelled] what the page asked. */
internal class FakeChatExportJobs : ChatExportJobs {
    val job = MutableStateFlow<ChatExportJob?>(null)
    val started = mutableListOf<Triple<Long, String, ChatExportOptions>>()
    val cancelled = mutableListOf<Long>()

    override fun start(
        chatId: Long,
        chatTitle: String,
        options: ChatExportOptions,
    ) {
        started += Triple(chatId, chatTitle, options)
    }

    override fun cancel(chatId: Long) {
        cancelled += chatId
    }

    override fun job(chatId: Long): Flow<ChatExportJob?> = job
}

/** Folders by tree URI: named [names], those not named refused; [released] the grants given back. */
internal class FakeExportFolders : ExportFolderRepository {
    var names = mapOf("content://tree/download" to "Download", "content://tree/documents" to "Documents")
    val kept = mutableListOf<String>()
    val released = mutableListOf<String>()

    override suspend fun keep(treeUri: String): Outcome<String> {
        val name = names[treeUri] ?: return Outcome.Failure(DomainError.FileUnavailable)
        kept += treeUri
        return Outcome.Success(name)
    }

    override suspend fun release(treeUri: String) {
        released += treeUri
    }
}
