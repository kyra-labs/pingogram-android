package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The transfers of a conversation, kept out of [ChatUiState] because they tick (`docs/PERFORMANCE.md`): how far the
 * files of the loaded messages are downloaded, by file id ([ChatFiles.statesOf]), and the uploads of the messages
 * being sent from this device, by message id ([ChatFiles.uploadsOf]). The list reads them once ([collect]) and each
 * bubble only its own ([TransferStates]), so a tick recomposes the bubble whose file it is, not the screen.
 */
@Stable
class ChatTransfers(
    val fileStates: StateFlow<ImmutableMap<Int, FileState>>,
    val uploads: StateFlow<ImmutableMap<Long, MessageUpload>>,
) {
    companion object {
        /** No transfers, as before the conversation is known. */
        val None: ChatTransfers = of()

        /** Transfers fixed at [fileStates] and [uploads], for previews and tests. */
        fun of(
            fileStates: ImmutableMap<Int, FileState> = persistentMapOf(),
            uploads: ImmutableMap<Long, MessageUpload> = persistentMapOf(),
        ): ChatTransfers = ChatTransfers(MutableStateFlow(fileStates), MutableStateFlow(uploads))
    }
}

/** The transfers, collected while the screen is at least started, for the bubbles to read their own. */
@Composable
internal fun ChatTransfers.collect(): TransferStates {
    val files = fileStates.collectAsStateWithLifecycle()
    val uploads = uploads.collectAsStateWithLifecycle()
    return remember(files, uploads) { TransferStates(files, uploads) }
}

/**
 * The collected transfers ([ChatTransfers.collect]). Reading one bubble's ([fileState], [uploadsOf]) recomposes the
 * reader only when that bubble's changes, not on every tick of another file.
 */
@Stable
internal class TransferStates(
    private val files: State<ImmutableMap<Int, FileState>>,
    private val uploads: State<ImmutableMap<Long, MessageUpload>>,
) {
    /** The state of file [fileId]; null for none, or one TDLib has not reported. */
    @Composable
    fun fileState(fileId: Int?): FileState? {
        val state = remember(fileId) { derivedStateOf(structuralEqualityPolicy()) { fileId?.let(files.value::get) } }
        return state.value
    }

    /** The uploads of [bubble]'s messages ([uploadsIn]). */
    @Composable
    fun uploadsOf(bubble: ChatItem.Bubble): ImmutableMap<Long, MessageUpload> {
        val ids = bubble.album?.messageIds ?: listOf(bubble.id)
        val state = remember(ids) { derivedStateOf(structuralEqualityPolicy()) { bubble.uploadsIn(uploads.value) } }
        return state.value
    }
}
