package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.RecommendedChatFolder
import dev.kyralabs.pingogram.core.domain.repository.ChatFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Chat folders page (`REQUIREMENTS.md` FR-L3): the user's folders in their order, from `ChatRepository.folders`,
 * each with what it includes (`ChatFolderRepository.folder`, read again whenever the folders change, here or on
 * another device), and the folders Telegram suggests.
 *
 * A new order shows at once and goes to Telegram with the main list kept where it is; refused, the folders return to
 * Telegram's order with a snackbar. A suggested folder is created as Telegram suggests it, and the suggestions are
 * asked for again.
 */
@HiltViewModel
class ChatFoldersViewModel
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val folderRepository: ChatFolderRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<ChatFoldersUiState>(ChatFoldersUiState.Loading)
        val uiState: StateFlow<ChatFoldersUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ChatFoldersEffect>(Channel.BUFFERED)
        val effects: Flow<ChatFoldersEffect> = effectChannel.receiveAsFlow()

        private val retries = MutableStateFlow(0)

        /** The main list's place among the folders, as TDLib reported it last. */
        private var mainChatListPosition = 0

        /** The suggestions, by their place, as Telegram gave them last. */
        private var recommended: List<RecommendedChatFolder> = emptyList()

        /** The suggestions being created. */
        private val adding = mutableSetOf<Int>()

        init {
            viewModelScope.launch {
                combine(chatRepository.folders, chatRepository.mainChatListPosition, retries) { folders, main, _ ->
                    folders to main
                }.collectLatest { (folders, main) ->
                    mainChatListPosition = main
                    load(folders)
                }
            }
            loadRecommended()
        }

        fun onEvent(event: ChatFoldersEvent) {
            when (event) {
                ChatFoldersEvent.Retry -> if (uiState.value is ChatFoldersUiState.Error) retries.update { it + 1 }
                is ChatFoldersEvent.Reorder -> reorder(event.folderIds)
                is ChatFoldersEvent.Move -> move(event.id, event.up)
                is ChatFoldersEvent.AddRecommended -> addRecommended(event.key)
            }
        }

        private suspend fun load(folders: List<ChatFolder>) {
            val settings =
                coroutineScope { folders.map { folder -> async { folderRepository.folder(folder.id) } }.awaitAll() }
            val failure = settings.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }
            if (failure != null) {
                if (uiState.value !is ChatFoldersUiState.Content) {
                    mutableUiState.value =
                        ChatFoldersUiState.Error(failure)
                }
                return
            }
            val items =
                folders.zip(settings) { folder, outcome ->
                    folder.toItem((outcome as Outcome.Success).value)
                }
            mutableUiState.update { state ->
                val recommendedItems = (state as? ChatFoldersUiState.Content)?.recommended ?: recommendedItems()
                ChatFoldersUiState.Content(folders = items.toImmutableList(), recommended = recommendedItems)
            }
        }

        private fun reorder(folderIds: List<Int>) {
            val content = uiState.value as? ChatFoldersUiState.Content ?: return
            val byId = content.folders.associateBy { it.id }
            if (folderIds.toSet() != byId.keys || folderIds == content.folders.map { it.id }) return
            mutableUiState.value = content.copy(folders = folderIds.map { byId.getValue(it) }.toImmutableList())
            viewModelScope.launch {
                val outcome = folderRepository.reorderFolders(folderIds, mainChatListPosition)
                if (outcome is Outcome.Failure) {
                    // TDLib's order returns with the next read of the folders.
                    retries.update { it + 1 }
                    showError(outcome.error)
                }
            }
        }

        private fun move(
            id: Int,
            up: Boolean,
        ) {
            val ids = (uiState.value as? ChatFoldersUiState.Content)?.folders?.map { it.id } ?: return
            val index = ids.indexOf(id)
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            reorder(ids.toMutableList().apply { add(target, removeAt(index)) })
        }

        private fun loadRecommended() {
            viewModelScope.launch {
                val outcome = folderRepository.recommendedFolders()
                // Suggestions are optional: without them the page shows the user's folders alone.
                recommended = (outcome as? Outcome.Success)?.value.orEmpty()
                mutableUiState.update { state ->
                    (state as? ChatFoldersUiState.Content)?.copy(recommended = recommendedItems()) ?: state
                }
            }
        }

        private fun addRecommended(key: Int) {
            val folder = recommended.getOrNull(key) ?: return
            if (!adding.add(key)) return
            updateRecommended()
            viewModelScope.launch {
                val outcome = folderRepository.createFolder(folder.settings)
                adding.remove(key)
                if (outcome is Outcome.Failure) {
                    updateRecommended()
                    showError(outcome.error)
                } else {
                    loadRecommended()
                }
            }
        }

        private fun updateRecommended() {
            mutableUiState.update { state ->
                (state as? ChatFoldersUiState.Content)?.copy(recommended = recommendedItems()) ?: state
            }
        }

        private fun recommendedItems() =
            recommended
                .mapIndexed { key, folder ->
                    RecommendedFolderItem(
                        key = key,
                        title = folder.settings.title.text,
                        iconName = folder.settings.iconName,
                        description = folder.description,
                        isAdding = key in adding,
                    )
                }.toImmutableList()

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(ChatFoldersEffect.ShowError(error))
        }
    }

private fun ChatFolder.toItem(settings: ChatFolderSettings) =
    FolderItem(
        id = id,
        title = title.text,
        iconName = iconName,
        includedTypes = settings.includedTypes.toImmutableSet(),
        chatCount = (settings.pinnedChatIds + settings.includedChatIds).distinct().size,
        isShared = settings.isShareable,
    )
