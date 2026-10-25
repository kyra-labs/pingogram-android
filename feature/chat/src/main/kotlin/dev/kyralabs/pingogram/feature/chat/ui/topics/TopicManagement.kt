package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicLimits
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Managing a forum's topics (`REQUIREMENTS.md` FR-C9), a part of the view models of the topic list
 * ([ForumTopicsViewModel]) and of a topic's conversation ([TopicActionsViewModel]): the editor of a new or a renamed
 * topic, the question before deleting one, and the toggles of a topic's menu (close, pin, hide General), each sent to
 * [repository] once, in [scope]. What the screens show of them is [state]; what comes of them is [effects]: a created
 * topic to open, a deleted one, a refusal for a snackbar. [topicOf] finds the topic a menu was opened for.
 *
 * The editor (the topic, the name typed, the color picked) and the question are kept in [savedStateHandle], so they
 * survive process death; what is on its way to Telegram is not, and asks again.
 */
internal class TopicManagement(
    private val chatId: Long,
    private val repository: ForumTopicRepository,
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val topicOf: (topicId: Int) -> ForumTopic?,
) {
    private val mutableState = MutableStateFlow(restored())

    val state: StateFlow<TopicManagementUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<TopicEffect>(Channel.BUFFERED)

    val effects: Flow<TopicEffect> = effectChannel.receiveAsFlow()

    @Suppress("CyclomaticComplexMethod") // One branch per event, each a line or two.
    fun onEvent(event: TopicEvent) {
        when (event) {
            TopicEvent.NewTopic -> openEditor(TopicEditorUiState(topicId = null, name = "", color = TopicColor.Blue))
            is TopicEvent.Edit -> topicOf(event.topicId)?.let { openEditor(it.editor()) }
            is TopicEvent.NameChanged -> editEditor { it.copy(name = event.name.take(MAX_TYPED), error = null) }
            is TopicEvent.ColorPicked -> editEditor { it.copy(color = event.color) }
            TopicEvent.Save -> save()
            TopicEvent.DismissEditor -> setEditor(null)
            is TopicEvent.SetClosed -> send { repository.setClosed(chatId, event.topicId, event.isClosed) }
            is TopicEvent.SetPinned -> send { repository.setPinned(chatId, event.topicId, event.isPinned) }
            is TopicEvent.SetGeneralHidden -> send { repository.setGeneralHidden(chatId, event.isHidden) }
            is TopicEvent.Delete -> topicOf(event.topicId)?.let { setDeleting(TopicDeleteUiState(it.id, it.name)) }
            TopicEvent.ConfirmDelete -> delete()
            TopicEvent.DismissDelete -> setDeleting(null)
        }
    }

    private fun openEditor(editor: TopicEditorUiState) = setEditor(editor)

    private fun editEditor(edit: (TopicEditorUiState) -> TopicEditorUiState) {
        val editor = mutableState.value.editor ?: return
        if (editor.isSaving) return
        setEditor(edit(editor))
    }

    private fun save() {
        val editor = mutableState.value.editor ?: return
        if (!editor.canSave) return
        mutableState.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
        scope.launch {
            val topicId = editor.topicId
            // The new topic's id; null for a renamed one.
            val result: Outcome<Int?> =
                if (topicId == null) {
                    repository.createTopic(chatId, editor.name, editor.color).map { it }
                } else {
                    repository.renameTopic(chatId, topicId, editor.name).map { null }
                }
            when (result) {
                is Outcome.Success -> {
                    setEditor(null)
                    result.value?.let { effectChannel.send(TopicEffect.Created(it)) }
                }

                is Outcome.Failure -> {
                    mutableState.update { state ->
                        state.copy(editor = state.editor?.copy(isSaving = false, error = result.error))
                    }
                }
            }
        }
    }

    private fun delete() {
        val deleting = mutableState.value.deleting ?: return
        if (deleting.isDeleting) return
        mutableState.update { it.copy(deleting = deleting.copy(isDeleting = true)) }
        scope.launch {
            when (val result = repository.deleteTopic(chatId, deleting.topicId)) {
                is Outcome.Success -> {
                    setDeleting(null)
                    effectChannel.send(TopicEffect.Deleted(deleting.topicId))
                }

                is Outcome.Failure -> {
                    setDeleting(null)
                    effectChannel.send(TopicEffect.Failed(result.error))
                }
            }
        }
    }

    /** Sends a toggle of a topic's menu; its change comes back through TDLib's updates, a refusal as a snackbar. */
    private fun send(request: suspend () -> Outcome<Unit>) {
        scope.launch {
            (request() as? Outcome.Failure)?.let { effectChannel.send(TopicEffect.Failed(it.error)) }
        }
    }

    private fun setEditor(editor: TopicEditorUiState?) {
        mutableState.update { it.copy(editor = editor) }
        savedStateHandle[KEY_EDITOR] = editor?.let { if (it.topicId == null) NEW_TOPIC else it.topicId }
        savedStateHandle[KEY_NAME] = editor?.name
        savedStateHandle[KEY_COLOR] = editor?.color?.name
        savedStateHandle[KEY_GENERAL] = editor?.isGeneral
    }

    private fun setDeleting(deleting: TopicDeleteUiState?) {
        mutableState.update { it.copy(deleting = deleting) }
        savedStateHandle[KEY_DELETE] = deleting?.topicId
        savedStateHandle[KEY_DELETE_NAME] = deleting?.name
    }

    /** The editor and the question as the saved state left them. */
    private fun restored(): TopicManagementUiState {
        val editorTopic: Int? = savedStateHandle[KEY_EDITOR]
        val editor =
            editorTopic?.let { id ->
                TopicEditorUiState(
                    topicId = id.takeIf { it != NEW_TOPIC },
                    name = savedStateHandle[KEY_NAME] ?: "",
                    color =
                        savedStateHandle.get<String>(KEY_COLOR)?.let { name ->
                            TopicColor.entries.firstOrNull { it.name == name }
                        } ?: TopicColor.Blue,
                    isGeneral = savedStateHandle[KEY_GENERAL] ?: false,
                )
            }
        val deleteTopic: Int? = savedStateHandle[KEY_DELETE]
        val deleting = deleteTopic?.let { TopicDeleteUiState(it, savedStateHandle[KEY_DELETE_NAME] ?: "") }
        return TopicManagementUiState(editor, deleting)
    }

    private companion object {
        const val KEY_EDITOR = "topics.editor"
        const val KEY_NAME = "topics.editor.name"
        const val KEY_COLOR = "topics.editor.color"
        const val KEY_GENERAL = "topics.editor.general"
        const val KEY_DELETE = "topics.delete"
        const val KEY_DELETE_NAME = "topics.delete.name"

        /** The editor's topic id in the saved state while it creates one; topic ids are positive. */
        const val NEW_TOPIC = -1

        /** How much of a paste the field keeps: past the limit, so the counter says it is too long. */
        const val MAX_TYPED = ForumTopicLimits.MAX_NAME_LENGTH * 2
    }
}

/** The editor of [this] topic, with its name and color as they are. */
private fun ForumTopic.editor() =
    TopicEditorUiState(topicId = id, name = name, color = icon.color, isGeneral = isGeneral)

/** What managing topics shows: the editor of a new or a renamed topic, and the question before deleting one. */
@Immutable
data class TopicManagementUiState(
    val editor: TopicEditorUiState? = null,
    val deleting: TopicDeleteUiState? = null,
)

/**
 * The editor of a new topic ([topicId] null) or of topic [topicId]'s name: the [name] typed, the [color] picked (a new
 * topic's only: Telegram keeps a topic's color), whether it is on its way to Telegram ([isSaving]) and why Telegram
 * refused it ([error]).
 */
@Immutable
data class TopicEditorUiState(
    val topicId: Int?,
    val name: String,
    val color: TopicColor,
    val isGeneral: Boolean = false,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    val isNew: Boolean get() = topicId == null

    val isTooLong: Boolean get() = name.trim().length > ForumTopicLimits.MAX_NAME_LENGTH

    val canSave: Boolean get() = name.isNotBlank() && !isTooLong && !isSaving

    override fun toString() = "TopicEditorUiState(topicId=$topicId, color=$color, isSaving=$isSaving, error=$error, …)"
}

/** The question before deleting topic [topicId], named [name], and whether it is being deleted. */
@Immutable
data class TopicDeleteUiState(
    val topicId: Int,
    val name: String,
    val isDeleting: Boolean = false,
) {
    override fun toString() = "TopicDeleteUiState(topicId=$topicId, isDeleting=$isDeleting, …)"
}

/** What the user does to manage topics, sent to [TopicManagement.onEvent] through the screen's view model. */
sealed interface TopicEvent {
    /** "New topic" in the topic list's top bar. */
    data object NewTopic : TopicEvent

    /** "Edit" (rename) in a topic's menu. */
    data class Edit(
        val topicId: Int,
    ) : TopicEvent

    data class NameChanged(
        val name: String,
    ) : TopicEvent

    data class ColorPicked(
        val color: TopicColor,
    ) : TopicEvent

    /** "Create" or "Save" in the editor. */
    data object Save : TopicEvent

    data object DismissEditor : TopicEvent

    /** "Close topic" or "Reopen topic". */
    data class SetClosed(
        val topicId: Int,
        val isClosed: Boolean,
    ) : TopicEvent

    /** "Pin" or "Unpin". */
    data class SetPinned(
        val topicId: Int,
        val isPinned: Boolean,
    ) : TopicEvent

    /** "Hide General" or "Show General". */
    data class SetGeneralHidden(
        val isHidden: Boolean,
    ) : TopicEvent

    /** "Delete" in a topic's menu, which asks first. */
    data class Delete(
        val topicId: Int,
    ) : TopicEvent

    data object ConfirmDelete : TopicEvent

    data object DismissDelete : TopicEvent
}

/** What comes of managing topics, for the screen's view model to pass on. */
sealed interface TopicEffect {
    /** Topic [topicId] was created: it opens. */
    data class Created(
        val topicId: Int,
    ) : TopicEffect

    /** Topic [topicId] was deleted with its messages. */
    data class Deleted(
        val topicId: Int,
    ) : TopicEffect

    /** Telegram refused, or could not be reached. */
    data class Failed(
        val error: DomainError,
    ) : TopicEffect
}
