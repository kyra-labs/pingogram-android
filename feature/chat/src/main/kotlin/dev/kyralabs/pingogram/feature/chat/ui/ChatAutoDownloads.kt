package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Automatic download in a conversation (`docs/PERFORMANCE.md` §7), for [ChatViewModel], under the [rules] in effect on
 * the current network:
 *
 * - **Files**: the file of each message on screen ([setVisible]) that the rules allow
 *   ([AutoDownloadRules.fileToDownload]: videos and GIFs, documents and music within their size limits, voice and video
 *   messages) downloads at prefetch priority, once per file while the view model lives: a download the user cancelled
 *   does not start again by itself. Nothing is cancelled when the rules change or a message leaves the screen: the
 *   media viewer may be showing the same file. Only messages on screen count, never the whole history.
 * - **Photos** download in their bubbles while the rules allow photos ([state]); otherwise each shows a download
 *   button, and the photos the user asked for ([download]) show a download in progress until they show.
 *
 * A failed download is dropped: the file shows its download button, or downloads when tapped.
 */
internal class ChatAutoDownloads(
    private val repository: FileRepository,
    private val files: ChatFiles,
    private val rules: Flow<AutoDownloadRules>,
    private val scope: CoroutineScope,
) {
    private val visible = MutableStateFlow<List<MessageContent>>(emptyList())
    private val requestedFiles = MutableStateFlow<PersistentSet<Int>>(persistentSetOf())

    /** The files this view model started downloading by itself; only touched by [start]'s collector. */
    private val started = HashSet<Int>()

    /** Whether photos download by themselves now, and the files the user asked to download. */
    val state: Flow<MediaDownloads> =
        combine(rules.map { it.photos }.distinctUntilChanged(), requestedFiles, ::MediaDownloads)

    /** Starts following the messages on screen and the rules, until the scope ends. */
    fun start() {
        scope.launch {
            combine(rules, visible) { rules, contents -> contents.mapNotNull(rules::fileToDownload) }
                .collect { files ->
                    for (file in files) {
                        if (started.add(file.id)) launch { repository.download(file.id, DownloadPriority.Prefetch) }
                    }
                }
        }
    }

    /** The contents of the messages on screen now. */
    fun setVisible(contents: List<MessageContent>) {
        visible.value = contents
    }

    /** The user asked to download [fileId]: a document's, or a photo's the rules leave for a tap ([ChatFiles]). */
    fun download(fileId: Int) {
        requestedFiles.update { it.add(fileId) }
        files.download(fileId)
    }

    /** The user cancelled the download of [fileId]. */
    fun cancelDownload(fileId: Int) {
        requestedFiles.update { it.remove(fileId) }
        files.cancelDownload(fileId)
    }
}
