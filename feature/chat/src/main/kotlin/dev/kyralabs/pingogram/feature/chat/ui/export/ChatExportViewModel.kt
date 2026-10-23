package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatExportJob
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportStatus
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportJobs
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository
import dev.kyralabs.pingogram.core.navigation.ChatExportRoute
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The export page of chat [chatId] (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`): what to export and where,
 * then the export as [ChatExportJobs] runs it in the background.
 *
 * - **The chat** is read once ([ChatExportRepository.chat]) after the page's first frame: secret chats and chats that
 *   restrict saving their content cannot be exported ([ChatExportUiState.NotAllowed]).
 * - **The form** ([ExportForm]) is kept in [savedState], so what the user chose survives process death. A folder picked
 *   through the Storage Access Framework is kept ([ExportFolderRepository.keep]), and given back when another is
 *   picked or the page closes without exporting; once an export starts, its worker holds the folder and gives it back.
 * - **The export** is followed through [ChatExportJobs.job]: one running for this chat shows its progress whoever
 *   started it; one that ended shows its result while the page follows it (it started or saw it running), until
 *   [ChatExportEvent.Dismiss] goes back to the form, which then needs a folder again.
 */
@Suppress("TooManyFunctions") // One function per event, and the form's saved state.
@HiltViewModel
class ChatExportViewModel internal constructor(
    private val chatId: Long,
    private val savedState: SavedStateHandle,
    private val exportRepository: ChatExportRepository,
    private val jobs: ChatExportJobs,
    private val folders: ExportFolderRepository,
    private val applicationScope: CoroutineScope,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        exportRepository: ChatExportRepository,
        jobs: ChatExportJobs,
        folders: ExportFolderRepository,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : this(
        savedStateHandle.toRoute<ChatExportRoute>().chatId,
        savedStateHandle,
        exportRepository,
        jobs,
        folders,
        applicationScope,
    )

    private val chat = MutableStateFlow<ChatLoad>(ChatLoad.Loading)
    private val form = MutableStateFlow(restoreForm())

    /** The folder of [form], as a tree URI; null until one is picked, and again once an export took it. */
    private var folderUri: String?
        get() = savedState[FOLDER_URI]
        set(value) {
            savedState[FOLDER_URI] = value
        }

    /** The page started the export shown, or saw it running: its result shows once it ends. */
    private val following = savedState.getStateFlow(FOLLOWING, false)

    /** The export whose result the user dismissed, which then shows no more. */
    private val dismissed = savedState.getStateFlow<String?>(DISMISSED, null)

    private val latestJob = MutableStateFlow<ChatExportJob?>(null)

    private val effectChannel = Channel<ChatExportEffect>(Channel.BUFFERED)
    val effects: Flow<ChatExportEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<ChatExportUiState> =
        combine(chat, form, latestJob, following, dismissed) { chat, form, job, following, dismissed ->
            when (chat) {
                ChatLoad.Loading -> ChatExportUiState.Loading
                is ChatLoad.Failed -> ChatExportUiState.Error(chat.error)
                is ChatLoad.NotAllowed -> ChatExportUiState.NotAllowed(chat.isSecretChat)
                is ChatLoad.Loaded -> ChatExportUiState.Content(chat.title, form, job?.run(following, dismissed))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatExportUiState.Loading)

    init {
        // After the page's first frame (docs/PERFORMANCE.md §4).
        viewModelScope.launch(Dispatchers.Main) {
            load()
            jobs.job(chatId).collect(::follow)
        }
    }

    fun onEvent(event: ChatExportEvent) {
        when (event) {
            is ChatExportEvent.SetFormat -> {
                change { copy(format = event.format) }
            }

            is ChatExportEvent.SetMedia -> {
                change { copy(media = withMedia(event.kind, event.included)) }
            }

            is ChatExportEvent.SetSizeLimit -> {
                change {
                    copy(
                        sizeLimit = event.step.coerceIn(ExportSizeLimits.indices),
                    )
                }
            }

            ChatExportEvent.AllMessages -> {
                change { copy(sinceDay = null) }
            }

            is ChatExportEvent.Since -> {
                change { copy(sinceDay = event.day) }
            }

            is ChatExportEvent.FolderPicked -> {
                pickFolder(event.treeUri)
            }

            is ChatExportEvent.Export -> {
                export(event.chatTitle)
            }

            ChatExportEvent.Cancel -> {
                cancel()
            }

            ChatExportEvent.Dismiss -> {
                dismiss()
            }

            ChatExportEvent.Retry -> {
                retry()
            }
        }
    }

    /** Starts the export of the form, the notification naming the chat [title]. */
    private fun export(title: String) {
        val uri = folderUri ?: return
        if (latestJob.value?.status is ChatExportStatus.Running) return
        val chosen = form.value
        val since = chosen.sinceDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(zone()).toEpochSecond() }
        val options =
            ChatExportOptions(
                format = chosen.format,
                media = chosen.media,
                maxFileSize = ExportSizeLimits[chosen.sizeLimit],
                since = since,
                folderUri = uri,
            )
        // The result of an earlier export no longer shows; the new one's will.
        latestJob.value?.takeIf { it.status !is ChatExportStatus.Running }?.let { savedState[DISMISSED] = it.id }
        savedState[FOLLOWING] = true
        // The worker holds the folder now, and gives it back when it ends.
        folderUri = null
        change { copy(folderName = null) }
        jobs.start(chatId, title, options)
    }

    private suspend fun load() {
        chat.value =
            when (val found = exportRepository.chat(chatId)) {
                is Outcome.Failure -> {
                    ChatLoad.Failed(found.error)
                }

                is Outcome.Success -> {
                    val (exportChat, access) = found.value
                    when (access) {
                        ChatExportAccess.Allowed -> {
                            ChatLoad.Loaded(
                                if (exportChat.kind == ExportChatKind.SavedMessages) {
                                    UiText.resource(R.string.chat_saved_messages)
                                } else {
                                    UiText.Plain(exportChat.title)
                                },
                            )
                        }

                        ChatExportAccess.SecretChat -> {
                            ChatLoad.NotAllowed(isSecretChat = true)
                        }

                        ChatExportAccess.ProtectedContent -> {
                            ChatLoad.NotAllowed(isSecretChat = false)
                        }
                    }
                }
            }
    }

    private fun follow(job: ChatExportJob?) {
        latestJob.value = job
        if (job?.status is ChatExportStatus.Running) savedState[FOLLOWING] = true
    }

    private fun retry() {
        if (chat.value !is ChatLoad.Failed) return
        chat.value = ChatLoad.Loading
        viewModelScope.launch { load() }
    }

    private fun cancel() {
        if (latestJob.value?.status !is ChatExportStatus.Running) return
        jobs.cancel(chatId)
        effectChannel.trySend(ChatExportEffect.Cancelled)
    }

    private fun dismiss() {
        latestJob.value?.takeIf { it.status !is ChatExportStatus.Running }?.let { savedState[DISMISSED] = it.id }
    }

    private fun pickFolder(treeUri: String) {
        val previous = folderUri
        change { copy(isPickingFolder = true) }
        viewModelScope.launch {
            when (val kept = folders.keep(treeUri)) {
                is Outcome.Success -> {
                    if (previous != null && previous != treeUri) folders.release(previous)
                    folderUri = treeUri
                    change { copy(folderName = kept.value, isPickingFolder = false) }
                }

                is Outcome.Failure -> {
                    change { copy(isPickingFolder = false) }
                    effectChannel.trySend(ChatExportEffect.FolderUnusable)
                }
            }
        }
    }

    private fun change(transform: ExportForm.() -> ExportForm) {
        form.update(transform)
        save(form.value)
    }

    private fun save(form: ExportForm) {
        savedState[FORMAT] = form.format.name
        savedState[MEDIA] = form.media.map { it.name }.toTypedArray()
        savedState[SIZE_LIMIT] = form.sizeLimit
        savedState[SINCE_DAY] = form.sinceDay
        savedState[FOLDER_NAME] = form.folderName
    }

    private fun restoreForm(): ExportForm {
        val media = savedState.get<Array<String>>(MEDIA).orEmpty()
        return ExportForm(
            format =
                ExportFormat.entries.firstOrNull {
                    it.name ==
                        savedState.get<String>(
                            FORMAT,
                        )
                } ?: ExportFormat.Html,
            media = ExportMediaKind.entries.filter { it.name in media }.toImmutableSet(),
            sizeLimit =
                savedState.get<Int>(SIZE_LIMIT)?.coerceIn(ExportSizeLimits.indices) ?: ExportSizeLimits.lastIndex,
            sinceDay = savedState.get<Long>(SINCE_DAY),
            folderName = savedState.get<String>(FOLDER_NAME)?.takeIf { savedState.get<String>(FOLDER_URI) != null },
        )
    }

    override fun onCleared() {
        // A folder picked but not exported into: its grant goes back.
        folderUri?.let { uri -> applicationScope.launch { folders.release(uri) } }
    }

    private sealed interface ChatLoad {
        data object Loading : ChatLoad

        data class Failed(
            val error: DomainError,
        ) : ChatLoad

        data class NotAllowed(
            val isSecretChat: Boolean,
        ) : ChatLoad

        data class Loaded(
            val title: UiText,
        ) : ChatLoad
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FORMAT = "export.format"
        const val MEDIA = "export.media"
        const val SIZE_LIMIT = "export.sizeLimit"
        const val SINCE_DAY = "export.sinceDay"
        const val FOLDER_URI = "export.folderUri"
        const val FOLDER_NAME = "export.folderName"
        const val FOLLOWING = "export.following"
        const val DISMISSED = "export.dismissed"
    }
}

/** [this] with [kind] in or out. */
private fun ExportForm.withMedia(
    kind: ExportMediaKind,
    included: Boolean,
) = (if (included) media + kind else media - kind).toImmutableSet()

/** What the page shows of this export: a running one always, one that ended while [following] and not [dismissed]. */
private fun ChatExportJob.run(
    following: Boolean,
    dismissed: String?,
): ExportRun? {
    val shown = following && id != dismissed
    return when (val status = status) {
        is ChatExportStatus.Running -> ExportRun.Running(status.progress)
        is ChatExportStatus.Done -> ExportRun.Done(status.result).takeIf { shown }
        is ChatExportStatus.Failed -> ExportRun.Failed(status.error).takeIf { shown }
        ChatExportStatus.Cancelled -> null
    }
}
