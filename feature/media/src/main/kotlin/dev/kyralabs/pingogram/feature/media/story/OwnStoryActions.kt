package dev.kyralabs.pingogram.feature.media.story

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ⋮ menu of the user's own story and its Delete (board 26), for [StoryViewerViewModel]: [state] is the menu and
 * the question; [onPause] is told whether they pause the story. Deleting goes to TDLib in [scope]; once deleted, a
 * snackbar says so and the caller moves on; a refusal is a snackbar and the story stays.
 */
internal class OwnStoryActions(
    private val repository: StoryRepository,
    private val scope: CoroutineScope,
    private val effects: SendChannel<StoryViewerEffect>,
    private val onPause: (Boolean) -> Unit,
) {
    private val mutableState = MutableStateFlow(OwnStoryUi())
    val state: StateFlow<OwnStoryUi> = mutableState.asStateFlow()

    /** Handles [event] for story [key] (null while none shows); [onDeleted] gets the story once TDLib deleted it. */
    fun onEvent(
        event: StoryViewerEvent.Own,
        key: StoryKey?,
        onDeleted: (StoryKey) -> Unit,
    ) {
        when (event) {
            is StoryViewerEvent.Own.MenuOpen -> mutableState.update { it.copy(menuOpen = event.open) }
            StoryViewerEvent.Own.Delete -> mutableState.update { it.copy(menuOpen = false, deleteAsked = true) }
            StoryViewerEvent.Own.DeleteDismissed -> mutableState.update { it.copy(deleteAsked = it.isDeleting) }
            StoryViewerEvent.Own.DeleteConfirmed -> key?.let { delete(it, onDeleted) }
        }
        updatePause()
    }

    /** Another chat shows: the menu closes; a question still on screen stays. */
    fun closeMenu() {
        mutableState.update { it.copy(menuOpen = false) }
        updatePause()
    }

    private fun delete(
        key: StoryKey,
        onDeleted: (StoryKey) -> Unit,
    ) {
        if (mutableState.value.isDeleting) return
        mutableState.update { it.copy(isDeleting = true) }
        scope.launch {
            val deleted = repository.deleteStory(key.chatId, key.storyId)
            mutableState.value = OwnStoryUi()
            updatePause()
            when (deleted) {
                is Outcome.Success -> {
                    effects.trySend(StoryViewerEffect.StoryDeleted)
                    onDeleted(key)
                }

                is Outcome.Failure -> {
                    effects.trySend(StoryViewerEffect.ShowError(deleted.error))
                }
            }
        }
    }

    private fun updatePause() {
        val own = mutableState.value
        onPause(own.menuOpen || own.deleteAsked || own.isDeleting)
    }
}
