package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReactions
import dev.kyralabs.pingogram.core.domain.model.ChatSettings
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.rights
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatSettingsRepository
import dev.kyralabs.pingogram.core.navigation.ChatSettingsRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings page of group or channel [chatId] (`REQUIREMENTS.md` FR-P3): reactions, restricting saving content
 * (the owner's), signatures (channels), topics (a supergroup's owner), the discussion group, and upgrading a basic
 * group (its owner). Each change is one request; the page follows Telegram's answer through
 * [ChatSettingsRepository.settings], which TDLib's updates keep current, so a refusal leaves it as it was, with a
 * snackbar.
 */
@HiltViewModel
class ChatSettingsViewModel internal constructor(
    private val repository: ChatSettingsRepository,
    private val chatRepository: ChatRepository,
    private val chatId: Long,
) : ViewModel() {
    @Inject
    constructor(
        repository: ChatSettingsRepository,
        chatRepository: ChatRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(repository, chatRepository, savedStateHandle.toRoute<ChatSettingsRoute>().chatId)

    private val mutableUiState = MutableStateFlow<ChatSettingsUiState>(ChatSettingsUiState.Loading)
    val uiState: StateFlow<ChatSettingsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<ChatSettingsEffect>(Channel.BUFFERED)
    val effects: Flow<ChatSettingsEffect> = effectChannel.receiveAsFlow()

    private var settings: ChatSettings? = null
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: ChatSettingsEvent) {
        when (event) {
            ChatSettingsEvent.Retry -> {
                if (uiState.value is ChatSettingsUiState.Error) load()
            }

            is ChatSettingsEvent.ChooseReactions -> {
                chooseReactions(event.choice)
            }

            is ChatSettingsEvent.ToggleReaction -> {
                toggleReaction(event.emoji)
            }

            is ChatSettingsEvent.SetProtectedContent -> {
                save { repository.setProtectedContent(chatId, event.on) }
            }

            is ChatSettingsEvent.SetSignMessages -> {
                save {
                    repository.setSignMessages(
                        chatId,
                        event.sign,
                        event.showProfiles,
                    )
                }
            }

            is ChatSettingsEvent.SetTopics -> {
                save { repository.setForum(chatId, event.enabled, event.tabs) }
            }

            ChatSettingsEvent.OpenDiscussionPicker -> {
                openDiscussionPicker()
            }

            ChatSettingsEvent.CloseDiscussionPicker -> {
                updateContent { it.copy(discussionPicker = null) }
            }

            is ChatSettingsEvent.LinkDiscussion -> {
                link(event.chatId)
            }

            ChatSettingsEvent.Unlink -> {
                updateContent { it.copy(dialog = ChatSettingsDialog.Unlink) }
            }

            ChatSettingsEvent.Upgrade -> {
                updateContent { it.copy(dialog = ChatSettingsDialog.Upgrade) }
            }

            ChatSettingsEvent.ConfirmDialog -> {
                confirm()
            }

            ChatSettingsEvent.DismissDialog -> {
                updateContent { it.copy(dialog = null) }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        mutableUiState.value = ChatSettingsUiState.Loading
        loadJob =
            viewModelScope.launch {
                repository
                    .settings(chatId)
                    .flatMapLatest { outcome ->
                        val linked = (outcome as? Outcome.Success)?.value?.linkedChatId ?: 0
                        val title = if (linked == 0L) flowOf(null) else chatRepository.chat(linked).map { it?.title }
                        combine(title, repository.activeReactions) { linkedTitle, active ->
                            Triple(outcome, linkedTitle, active)
                        }
                    }.collect { (outcome, linkedTitle, active) ->
                        when (outcome) {
                            is Outcome.Success -> {
                                settings = outcome.value
                                show(outcome.value, linkedTitle, active)
                            }

                            is Outcome.Failure -> {
                                if (uiState.value !is ChatSettingsUiState.Content) {
                                    mutableUiState.value = ChatSettingsUiState.Error(outcome.error)
                                }
                            }
                        }
                    }
            }
    }

    private fun show(
        settings: ChatSettings,
        linkedTitle: String?,
        active: ImmutableList<String>,
    ) {
        val role = settings.myRole
        val isOwner = role is MemberRole.Owner
        val rights = role.rights
        val canChangeInfo = isOwner || rights.canChangeInfo
        val isChannel = settings.kind == GroupKind.Channel
        val content =
            ChatSettingsUiState.Content(
                kind = settings.kind,
                reactions = settings.reactions.toSetting(active).takeIf { canChangeInfo },
                protectedContent = settings.hasProtectedContent.takeIf { isOwner },
                signMessages =
                    SignMessagesSetting(settings.signMessages, settings.showMessageSender)
                        .takeIf { isChannel && canChangeInfo },
                topics =
                    TopicsSetting(settings.isForum, settings.hasForumTabs)
                        .takeIf { settings.kind == GroupKind.Supergroup && isOwner },
                discussion =
                    DiscussionSetting(settings.linkedChatId, linkedTitle, canChange = isChannel && canChangeInfo)
                        .takeIf { settings.kind != GroupKind.BasicGroup && (it.canChange || it.chatId != 0L) },
                canUpgrade = settings.kind == GroupKind.BasicGroup && isOwner,
            )
        mutableUiState.update { state ->
            if (state is ChatSettingsUiState.Content) {
                content.copy(
                    isSaving = state.isSaving,
                    dialog = state.dialog,
                    discussionPicker = state.discussionPicker,
                )
            } else {
                content
            }
        }
    }

    private fun chooseReactions(choice: ReactionsChoice) {
        val current = settings?.reactions
        val setting = (uiState.value as? ChatSettingsUiState.Content)?.reactions
        if (current == null || setting == null || choice == setting.choice) return
        val reactions =
            when (choice) {
                ReactionsChoice.All -> {
                    ChatReactions.All(current.maxCount)
                }

                // From every reaction, Telegram's apps start with all of them picked.
                ReactionsChoice.Some -> {
                    ChatReactions.Some(
                        setting.available.map { ReactionType.Emoji(it) }.toImmutableList(),
                        current.maxCount,
                    )
                }

                ReactionsChoice.None -> {
                    ChatReactions.Some(persistentListOf(), current.maxCount)
                }
            }
        save { repository.setReactions(chatId, reactions) }
    }

    private fun toggleReaction(emoji: String) {
        val current = settings?.reactions as? ChatReactions.Some ?: return
        val picked = ReactionType.Emoji(emoji)
        val reactions = if (picked in current.reactions) current.reactions - picked else current.reactions + picked
        save { repository.setReactions(chatId, current.copy(reactions = reactions.toImmutableList())) }
    }

    private fun openDiscussionPicker() {
        updateContent { it.copy(discussionPicker = DiscussionPicker.Loading) }
        viewModelScope.launch {
            val picker =
                when (val found = repository.discussionGroupCandidates()) {
                    is Outcome.Success -> {
                        val groups =
                            found.value.mapNotNull { id ->
                                chatRepository.chat(id).first()?.let { DiscussionGroup(id, it.title) }
                            }
                        DiscussionPicker.Groups(groups.toImmutableList())
                    }

                    is Outcome.Failure -> {
                        DiscussionPicker.Failed(found.error)
                    }
                }
            updateContent { if (it.discussionPicker == null) it else it.copy(discussionPicker = picker) }
        }
    }

    private fun link(groupChatId: Long) {
        updateContent { it.copy(discussionPicker = null) }
        save { repository.setDiscussionGroup(chatId, groupChatId) }
    }

    private fun confirm() {
        val content = uiState.value as? ChatSettingsUiState.Content
        val dialog = content?.dialog ?: return
        mutableUiState.value = content.copy(dialog = null)
        when (dialog) {
            ChatSettingsDialog.Unlink -> {
                // A group's link is changed from its channel; a channel unlinks its group.
                val linked = content.discussion?.chatId ?: 0
                val channel = if (content.kind == GroupKind.Channel) chatId else linked
                save { repository.setDiscussionGroup(channel, 0) }
            }

            ChatSettingsDialog.Upgrade -> {
                save {
                    when (val upgraded = repository.upgradeToSupergroup(chatId)) {
                        is Outcome.Success -> {
                            effectChannel.trySend(ChatSettingsEffect.Upgraded(upgraded.value))
                            Outcome.Success(Unit)
                        }

                        is Outcome.Failure -> {
                            upgraded
                        }
                    }
                }
            }
        }
    }

    /** Sends [request] while the page waits; a refusal is a snackbar, and the page keeps showing Telegram's state. */
    private fun save(request: suspend () -> Outcome<Unit>) {
        val content = uiState.value as? ChatSettingsUiState.Content ?: return
        if (content.isSaving) return
        mutableUiState.value = content.copy(isSaving = true)
        viewModelScope.launch {
            val error = (request() as? Outcome.Failure)?.error
            updateContent { it.copy(isSaving = false) }
            if (error != null && error != DomainError.Silent) effectChannel.trySend(ChatSettingsEffect.ShowError(error))
        }
    }

    private fun updateContent(transform: (ChatSettingsUiState.Content) -> ChatSettingsUiState.Content) =
        mutableUiState.update { if (it is ChatSettingsUiState.Content) transform(it) else it }
}

/** The reactions as the page shows them; emoji the chat allows but Telegram no longer offers are listed too. */
private fun ChatReactions.toSetting(active: ImmutableList<String>): ReactionsSetting {
    val chosen = (this as? ChatReactions.Some)?.reactions.orEmpty().mapNotNull { (it as? ReactionType.Emoji)?.emoji }
    val choice =
        when {
            this is ChatReactions.All -> ReactionsChoice.All
            (this as ChatReactions.Some).reactions.isEmpty() -> ReactionsChoice.None
            else -> ReactionsChoice.Some
        }
    return ReactionsSetting(
        choice,
        chosen.toImmutableSet(),
        (active + chosen.filter { it !in active }).toImmutableList(),
    )
}
