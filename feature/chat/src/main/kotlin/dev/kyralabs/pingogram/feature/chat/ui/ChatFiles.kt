package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * The files of a conversation's voice messages, songs and documents, for [ChatViewModel]: their states through
 * [FileRepository.observe], and downloads at visible priority in [scope]. A failed request goes to [onError]. Playing
 * a voice message or song downloads its file through [ChatPlayback].
 */
internal class ChatFiles(
    private val repository: FileRepository,
    private val scope: CoroutineScope,
    private val onError: (DomainError) -> Unit,
) {
    /**
     * The state of every file of the messages in [history], by id: one [FileRepository.observe] per file, followed for
     * as long as its message is loaded. A file TDLib has not reported is left out until it is.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun statesOf(history: Flow<ChatHistory>): Flow<ImmutableMap<Int, FileState>> =
        history
            .map { it.messages.mapNotNullTo(LinkedHashSet()) { message -> message.content.downloadableFileId } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map(::observed)) { files ->
                        files.mapNotNull { (id, state) -> state?.let { id to it } }.toMap().toImmutableMap()
                    }
                }
            }.distinctUntilChanged()

    fun download(fileId: Int) = request { download(fileId, DownloadPriority.Visible) }

    fun cancelDownload(fileId: Int) = request { cancelDownload(fileId) }

    private fun request(call: suspend FileRepository.() -> Outcome<Unit>) {
        scope.launch { repository.call().onFailure(onError) }
    }

    /** [fileId] with its state as soon as it is subscribed: null until TDLib reports it. */
    private fun observed(fileId: Int): Flow<Pair<Int, FileState?>> =
        repository
            .observe(fileId)
            .map<FileState, Pair<Int, FileState?>> { fileId to it }
            .onStart { emit(fileId to null) }
}
