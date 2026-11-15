package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keyword alerts' page (`docs/DESIGN_SYSTEM.md` §13.13, board 30): the words, where to look and the two switches,
 * kept on this phone ([KeywordAlertsRepository]). The empty page suggests the account's first name and username, read
 * from TDLib's copy on the phone ([profile]), while not kept (the screen adds a few common words); nothing is added
 * until the user taps one (owner decision 2026-11-15). Each change is saved at once; one that fails is a snackbar. The
 * folders being chosen survive process death.
 */
@HiltViewModel
class KeywordAlertsViewModel internal constructor(
    private val repository: KeywordAlertsRepository,
    folders: Flow<ImmutableList<ChatFolder>>,
    profile: suspend () -> EditableProfile?,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    @Inject
    constructor(
        repository: KeywordAlertsRepository,
        chatRepository: ChatRepository,
        profileRepository: ProfileRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        repository,
        chatRepository.folders,
        { (profileRepository.editableProfile() as? Outcome.Success)?.value },
        savedStateHandle,
    )

    private val effectChannel = Channel<KeywordAlertsEffect>(Channel.BUFFERED)
    val effects: Flow<KeywordAlertsEffect> = effectChannel.receiveAsFlow()

    private val account = MutableStateFlow<List<KeywordSuggestion>>(emptyList())

    val uiState: StateFlow<KeywordAlertsUiState> =
        combine(
            repository.settings,
            folders,
            account,
            savedStateHandle.getStateFlow<IntArray?>(KEY_CHOOSING, null),
        ) { settings, chatFolders, accountWords, choosing ->
            val kept = settings.words.map(KeywordMatcher::keyOf).toSet()
            val suggestions = accountWords.filter { KeywordMatcher.keyOf(it.word) !in kept }
            KeywordAlertsUiState(
                settings = settings,
                folders = chatFolders.map { KeywordFolder(it.id, it.title.text) }.toImmutableList(),
                suggestions = suggestions.toImmutableList(),
                choosingFolders = choosing?.toList()?.toPersistentSet(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), KeywordAlertsUiState())

    init {
        viewModelScope.launch {
            val me = profile() ?: return@launch
            account.value =
                listOfNotNull(
                    me.firstName.trim().takeIf { it.isNotEmpty() }?.let {
                        KeywordSuggestion(it, KeywordSuggestion.Kind.Name)
                    },
                    me.username.trim().takeIf { it.isNotEmpty() }?.let {
                        KeywordSuggestion("@$it", KeywordSuggestion.Kind.Username)
                    },
                )
        }
    }

    fun onEvent(event: KeywordAlertsEvent) {
        when (event) {
            is KeywordAlertsEvent.Add -> {
                if (event.word.isNotBlank()) save(added = true) { repository.addWord(event.word) }
            }

            is KeywordAlertsEvent.Remove -> {
                save { repository.removeWord(event.word) }
            }

            is KeywordAlertsEvent.Where -> {
                save { repository.setWhere(event.where) }
            }

            KeywordAlertsEvent.ChooseFolders -> {
                val where = uiState.value.settings?.where
                savedStateHandle[KEY_CHOOSING] =
                    (where as? KeywordScope.Folders)?.folderIds?.toIntArray() ?: IntArray(0)
            }

            is KeywordAlertsEvent.ToggleFolder -> {
                val chosen = savedStateHandle.get<IntArray>(KEY_CHOOSING)?.toSet() ?: return
                savedStateHandle[KEY_CHOOSING] =
                    (if (event.folderId in chosen) chosen - event.folderId else chosen + event.folderId).toIntArray()
            }

            KeywordAlertsEvent.FoldersChosen -> {
                val chosen = savedStateHandle.get<IntArray>(KEY_CHOOSING)?.toSet() ?: return
                savedStateHandle[KEY_CHOOSING] = null
                if (chosen.isNotEmpty()) save { repository.setWhere(KeywordScope.Folders(chosen.toPersistentSet())) }
            }

            KeywordAlertsEvent.CloseFolders -> {
                savedStateHandle[KEY_CHOOSING] = null
            }

            is KeywordAlertsEvent.AlertWhenMuted -> {
                save { repository.setAlertWhenMuted(event.alert) }
            }

            is KeywordAlertsEvent.ShowInChatList -> {
                save { repository.setShowInChatList(event.show) }
            }
        }
    }

    private fun save(
        added: Boolean = false,
        change: suspend () -> Outcome<Unit>,
    ) {
        viewModelScope.launch {
            when (val outcome = change()) {
                is Outcome.Success -> if (added) effectChannel.trySend(KeywordAlertsEffect.Added)
                is Outcome.Failure -> effectChannel.trySend(KeywordAlertsEffect.ShowError(outcome.error))
            }
        }
    }

    private companion object {
        const val KEY_CHOOSING = "keywords.choosing_folders"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
