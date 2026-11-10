package dev.kyralabs.pingogram.feature.media.storypost

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository
import kotlinx.coroutines.CoroutineScope
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
import javax.inject.Inject

/**
 * Picking the photos and videos of a new story (board 10a, `docs/adr/0036-posting-stories.md`): Android's photo picker
 * opens as the screen first shows, the camera app takes photos, and the user reorders and removes items before going
 * on to the composer. Every picked file is copied into the app's cache by [AttachmentRepository] as it arrives, so the
 * picker's read grant may end; the copies are deleted when the user closes the screen or it is cleared without
 * handing them over. The items, their order and the shown one are kept in [savedState] for process death
 * ([StoryPicks]).
 *
 * Nothing needs a permission: the photo picker and the camera app hand over only what the user chose.
 */
@HiltViewModel
internal class NewStoryViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val attachments: AttachmentRepository,
        postRepository: StoryPostRepository,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : ViewModel() {
        private val picks = StoryPicks(savedState, attachments)
        private val preparing = MutableStateFlow(0)
        private val step = MutableStateFlow(savedState.restoredStep())
        private val effectChannel = Channel<NewStoryEffect>(Channel.BUFFERED)

        /** Composing the story once the items are picked: its caption, who it is for, posting. */
        val composer = StoryComposer(savedState, postRepository, viewModelScope, applicationScope)

        val uiState: StateFlow<NewStoryUiState> =
            combine(picks.items, picks.selected, preparing, step) { items, selected, preparing, step ->
                uiStateOf(items, selected, preparing).copy(step = step)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, uiStateOf(picks.items.value, picks.selected.value, 0))
        val effects: Flow<NewStoryEffect> = effectChannel.receiveAsFlow()

        /** The picked photos and videos, in their order. */
        val picked: List<Attachment> get() = picks.items.value

        /** Set while TDLib may read the copies, from posting on: clearing the view model then leaves them. */
        private var handedOver = false

        init {
            if (!savedState.started) {
                savedState.started = true
                effectChannel.trySend(NewStoryEffect.OpenPicker(MAX_STORY_ITEMS))
            }
            if (step.value == NewStoryStep.Compose) composer.start()
        }

        fun onEvent(event: NewStoryEvent) {
            when (event) {
                is NewStoryEvent.Picked -> {
                    picked(event.uris)
                }

                NewStoryEvent.AddMore -> {
                    remaining().takeIf { it > 0 }?.let {
                        effectChannel.trySend(
                            NewStoryEffect.OpenPicker(it),
                        )
                    }
                }

                NewStoryEvent.TakePhoto -> {
                    takePhoto()
                }

                is NewStoryEvent.PhotoTaken -> {
                    photoTaken(event.taken)
                }

                is NewStoryEvent.Select -> {
                    picks.select(event.key)
                }

                is NewStoryEvent.Remove -> {
                    picks.remove(event.key)
                }

                is NewStoryEvent.Move -> {
                    picks.move(event.key, event.to)
                }

                NewStoryEvent.Next -> {
                    if (uiState.value.canGoNext) setStep(NewStoryStep.Compose)
                }

                NewStoryEvent.Back -> {
                    setStep(NewStoryStep.Pick)
                }

                is NewStoryEvent.SetAudience -> {
                    composer.setAudience(event.audience)
                }

                NewStoryEvent.Post -> {
                    post()
                }

                NewStoryEvent.Close -> {
                    close()
                }
            }
        }

        private fun setStep(next: NewStoryStep) {
            step.value = next
            savedState.saveStep(next)
            if (next == NewStoryStep.Compose) composer.start()
        }

        /**
         * Posts the items with the composer's caption, the copies TDLib's from then on; the screen closes once TDLib
         * took them, and says what went wrong otherwise, the copies the user's again.
         */
        private fun post() {
            handedOver = true
            composer.post(
                items = picks.items.value,
                onPosted = { effectChannel.trySend(NewStoryEffect.Posted) },
                onFailed = { error ->
                    handedOver = false
                    effectChannel.trySend(NewStoryEffect.Failed(error))
                },
            )
        }

        override fun onCleared() {
            if (!handedOver) picks.discardAll()
        }

        private fun picked(uris: List<String>) {
            val accepted = uris.take(remaining())
            if (accepted.isEmpty()) {
                // The picker was cancelled with nothing picked yet: nothing to post, so the screen closes.
                if (picks.items.value.isEmpty() && preparing.value == 0) close()
                return
            }
            preparing.update { it + accepted.size }
            viewModelScope.launch {
                accepted.forEach { uri -> add(attachments.prepare(uri, AttachmentKind.Media)) }
            }
        }

        private fun takePhoto() {
            if (remaining() == 0) return
            viewModelScope.launch {
                when (val file = attachments.newCameraFile()) {
                    is Outcome.Success -> {
                        savedState.cameraFile = file.value
                        effectChannel.send(NewStoryEffect.LaunchCamera(file.value))
                    }

                    is Outcome.Failure -> {
                        effectChannel.send(NewStoryEffect.Failed(file.error))
                    }
                }
            }
        }

        private fun photoTaken(taken: Boolean) {
            val path = savedState.cameraFile ?: return
            savedState.cameraFile = null
            if (!taken) {
                attachments.discardCameraFile(path)
                return
            }
            preparing.update { it + 1 }
            viewModelScope.launch { add(attachments.prepareCameraPhoto(path)) }
        }

        /** Adds a prepared item at the end and shows it; what cannot go into a story is deleted and said. */
        private suspend fun add(prepared: Outcome<Attachment>) {
            preparing.update { (it - 1).coerceAtLeast(0) }
            val attachment = (prepared as? Outcome.Success)?.value
            when {
                prepared is Outcome.Failure -> {
                    effectChannel.send(NewStoryEffect.Failed(prepared.error))
                }

                attachment is Attachment.Document -> {
                    attachments.discard(listOf(attachment))
                    effectChannel.send(NewStoryEffect.NotPostable)
                }

                attachment != null -> {
                    picks.add(attachment)
                }
            }
        }

        private fun close() {
            picks.discardAll()
            effectChannel.trySend(NewStoryEffect.Close)
        }

        private fun remaining(): Int = (MAX_STORY_ITEMS - picks.items.value.size - preparing.value).coerceAtLeast(0)
    }
