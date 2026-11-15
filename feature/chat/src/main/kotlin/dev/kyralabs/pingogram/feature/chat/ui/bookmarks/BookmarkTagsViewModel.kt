package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editing the bookmarks' tags (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34c), kept on this phone
 * ([BookmarkRepository]): adding, renaming with a color, reordering and deleting after a question that says what
 * becomes of the bookmarks that have the tag. The draft and the question are in the saved state, so they survive
 * process death; each change is saved at once, a refusal a snackbar.
 */
@HiltViewModel
class BookmarkTagsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val bookmarks: BookmarkRepository,
    ) : ViewModel() {
        private val draftId = savedStateHandle.getStateFlow(KEY_DRAFT, NO_DRAFT)
        private val draftName = savedStateHandle.getStateFlow(KEY_NAME, "")
        private val draftColor = savedStateHandle.getStateFlow(KEY_COLOR, 0)
        private val deletingId = savedStateHandle.getStateFlow(KEY_DELETING, NO_DRAFT)

        private val effectChannel = Channel<BookmarkTagsEffect>(Channel.BUFFERED)
        val effects: Flow<BookmarkTagsEffect> = effectChannel.receiveAsFlow()

        private val draft: Flow<TagDraft?> =
            combine(draftId, draftName, draftColor) { id, name, color ->
                when (id) {
                    NO_DRAFT -> null
                    NEW_TAG -> TagDraft(id = null, name = name, color = color)
                    else -> TagDraft(id = id, name = name, color = color)
                }
            }

        val uiState: StateFlow<BookmarkTagsUiState> =
            combine(bookmarks.tags, bookmarks.bookmarks, draft, deletingId) { tags, all, draft, deleting ->
                val rows =
                    tags.map { tag ->
                        val having = all.filter { tag.id in it.tagIds }
                        TagRow(tag.id, tag.name, tag.color, having.size, having.count { it.tagIds.size == 1 })
                    }
                BookmarkTagsUiState.Content(
                    tags = rows.toImmutableList(),
                    draft = draft?.takeIf { it.id == null || rows.any { row -> row.id == it.id } },
                    deleting = rows.firstOrNull { it.id == deleting },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BookmarkTagsUiState.Loading)

        fun onEvent(event: BookmarkTagsEvent) {
            when (event) {
                BookmarkTagsEvent.StartNew -> startNew()
                is BookmarkTagsEvent.StartEdit -> startEdit(event.id)
                is BookmarkTagsEvent.EditName -> savedStateHandle[KEY_NAME] = event.name
                is BookmarkTagsEvent.EditColor -> savedStateHandle[KEY_COLOR] = event.color
                BookmarkTagsEvent.SaveDraft -> saveDraft()
                BookmarkTagsEvent.CancelDraft -> savedStateHandle[KEY_DRAFT] = NO_DRAFT
                is BookmarkTagsEvent.Reorder -> change { bookmarks.reorderTags(event.ids) }
                is BookmarkTagsEvent.Move -> move(event.id, event.up)
                is BookmarkTagsEvent.AskDelete -> savedStateHandle[KEY_DELETING] = event.id
                BookmarkTagsEvent.ConfirmDelete -> delete()
                BookmarkTagsEvent.CancelDelete -> savedStateHandle[KEY_DELETING] = NO_DRAFT
            }
        }

        private fun startNew() {
            viewModelScope.launch {
                val count = bookmarks.tags.first().size
                savedStateHandle[KEY_NAME] = ""
                savedStateHandle[KEY_COLOR] = count % TAG_COLORS
                savedStateHandle[KEY_DRAFT] = NEW_TAG
            }
        }

        private fun startEdit(id: Long) {
            viewModelScope.launch {
                val tag = bookmarks.tags.first().firstOrNull { it.id == id } ?: return@launch
                savedStateHandle[KEY_NAME] = tag.name
                savedStateHandle[KEY_COLOR] = tag.color
                savedStateHandle[KEY_DRAFT] = id
            }
        }

        private fun saveDraft() {
            val id = draftId.value.takeIf { it != NO_DRAFT } ?: return
            val name = draftName.value.trim()
            if (name.isEmpty() || name.length > BookmarkTagsLimits.NAME_LENGTH_MAX) return
            val color = draftColor.value
            savedStateHandle[KEY_DRAFT] = NO_DRAFT
            change {
                if (id == NEW_TAG) {
                    when (val added = bookmarks.addTag(name, color)) {
                        is Outcome.Success -> Outcome.Success(Unit)
                        is Outcome.Failure -> added
                    }
                } else {
                    bookmarks.updateTag(BookmarkTag(id, name, color))
                }
            }
        }

        private fun move(
            id: Long,
            up: Boolean,
        ) {
            viewModelScope.launch {
                val ids =
                    bookmarks.tags
                        .first()
                        .map { it.id }
                        .toMutableList()
                val from = ids.indexOf(id)
                val to = if (up) from - 1 else from + 1
                if (from < 0 || to !in ids.indices) return@launch
                ids.add(to, ids.removeAt(from))
                bookmarks.reorderTags(ids).onError()
            }
        }

        private fun delete() {
            val id = deletingId.value.takeIf { it != NO_DRAFT } ?: return
            savedStateHandle[KEY_DELETING] = NO_DRAFT
            viewModelScope.launch {
                val name =
                    bookmarks.tags
                        .first()
                        .firstOrNull { it.id == id }
                        ?.name ?: return@launch
                when (val outcome = bookmarks.removeTag(id)) {
                    is Outcome.Success -> effectChannel.trySend(BookmarkTagsEffect.Deleted(name))
                    is Outcome.Failure -> effectChannel.trySend(BookmarkTagsEffect.ShowError(outcome.error))
                }
            }
        }

        private fun change(write: suspend () -> Outcome<Unit>) {
            viewModelScope.launch { write().onError() }
        }

        private fun Outcome<Unit>.onError() {
            if (this is Outcome.Failure) effectChannel.trySend(BookmarkTagsEffect.ShowError(error))
        }

        private companion object {
            const val KEY_DRAFT = "bookmarkTags.draft"
            const val KEY_NAME = "bookmarkTags.name"
            const val KEY_COLOR = "bookmarkTags.color"
            const val KEY_DELETING = "bookmarkTags.deleting"
            const val NO_DRAFT = -1L
            const val NEW_TAG = 0L
            const val TAG_COLORS = 8
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
