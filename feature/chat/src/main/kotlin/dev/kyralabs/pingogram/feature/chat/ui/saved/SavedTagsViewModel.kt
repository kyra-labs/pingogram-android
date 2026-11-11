package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTag
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTagRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved Messages' tags (the owner's board 8, `docs/DESIGN_SYSTEM.md` §6 "Saved Messages tags"): the reactions the user
 * put on saved messages, with their labels and counts ([SavedMessagesTagRepository.tags]), as chips; the messages a
 * selected tag filters, newest first, [PAGE_SIZE] at a time; and renaming a tag's label.
 *
 * - **Selection** ([select]) is kept in the saved state, so the filtered list comes back after process death; a tag
 *   that leaves the list (no message has it any more) gives the selection back to "All".
 * - **Pages** load once a tag is selected and as the list nears its end ([loadMore]); a failure shows with "Try
 *   again" ([retry]).
 * - **Renaming** ([startRename], [rename]): Telegram sets the label (Telegram Premium's); its refusal is a snackbar
 *   ([effects]), never worked around. Which tag is being renamed is in the saved state; the text is the dialog's.
 */
@HiltViewModel
class SavedTagsViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val repository: SavedMessagesTagRepository,
        private val users: UserRepository,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val selectedKey: StateFlow<String?> = savedState.getStateFlow(KEY_SELECTED, null)
        private val renamingKey: StateFlow<String?> = savedState.getStateFlow(KEY_RENAMING, null)
        private val pages = MutableStateFlow(TaggedPages())
        private val saving = MutableStateFlow(false)
        private var loading: Job? = null

        private val effectChannel = Channel<SavedTagsEffect>(Channel.BUFFERED)

        /** Snackbars: a tag renamed, or why Telegram did not rename it or load its messages. */
        val effects: Flow<SavedTagsEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<SavedTagsUiState> =
            combine(
                repository.tags(),
                selectedKey,
                pages,
                renamingKey,
                saving,
            ) { tags, selected, pages, renaming, saving ->
                val chips = tags.map(SavedMessagesTag::toChip).toImmutableList()
                SavedTagsUiState(
                    tags = chips,
                    selected = chips.firstOrNull { it.key == selected },
                    results = pages.toResults(selected),
                    rename = chips.firstOrNull { it.key == renaming }?.let { SavedTagRename(it, saving) },
                )
            }.distinctUntilChanged()
                .flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SavedTagsUiState())

        init {
            // A tag selected before process death loads its messages again.
            selectedKey.value?.let(::load)
        }

        /** Filters by [tag]; null for "All", which shows the conversation again. */
        fun select(tag: SavedTagChip?) {
            savedState[KEY_SELECTED] = tag?.key
            loading?.cancel()
            pages.value = TaggedPages()
            tag?.key?.let(::load)
        }

        /** Loads the next page of the selected tag's messages, once at a time. */
        fun loadMore() {
            val key = selectedKey.value ?: return
            val current = pages.value
            if (current.key == key && current.canLoadMore) load(key)
        }

        /** Loads the page that failed again. */
        fun retry() {
            val key = selectedKey.value ?: return
            pages.update { it.copy(error = null) }
            load(key)
        }

        fun startRename(tag: SavedTagChip) {
            savedState[KEY_RENAMING] = tag.key
        }

        fun dismissRename() {
            if (!saving.value) savedState[KEY_RENAMING] = null
        }

        /** Sets the label of the tag being renamed to [label]; the dialog closes once Telegram took it. */
        fun rename(label: String) {
            // The key from the saved state, which startRename sets at once; the ui state follows it a frame later.
            val key = renamingKey.value ?: return
            val tag = uiState.value.tags.firstOrNull { it.key == key } ?: return
            if (saving.value) return
            saving.value = true
            viewModelScope.launch {
                val result = repository.setLabel(tag.reaction, label.take(SavedMessagesTagRepository.LABEL_LENGTH_MAX))
                saving.value = false
                when (result) {
                    is Outcome.Success -> {
                        savedState[KEY_RENAMING] = null
                        effectChannel.trySend(SavedTagsEffect.Renamed)
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(SavedTagsEffect.ShowError(result.error))
                    }
                }
            }
        }

        private fun load(key: String) {
            val reaction = reactionOf(key) ?: return
            val from = pages.value.takeIf { it.key == key }?.nextFromMessageId ?: 0
            pages.update { if (it.key == key) it.copy(isLoading = true) else TaggedPages(key = key, isLoading = true) }
            loading =
                viewModelScope.launch {
                    val myUserId = users.myUserId.value
                    when (val page = repository.taggedMessages(reaction, from, PAGE_SIZE)) {
                        is Outcome.Success -> {
                            val found = page.value
                            pages.update { current ->
                                current.copy(
                                    items =
                                        (
                                            current.items +
                                                found.messages.map {
                                                    it.toItem(
                                                        myUserId,
                                                    )
                                                }
                                        ).toImmutableList(),
                                    total = found.totalCount,
                                    nextFromMessageId = found.nextFromMessageId,
                                    hasMore = found.nextFromMessageId != 0L && found.messages.isNotEmpty(),
                                    isLoading = false,
                                )
                            }
                        }

                        is Outcome.Failure -> {
                            pages.update { it.copy(isLoading = false, error = page.error) }
                        }
                    }
                }
        }

        private companion object {
            const val KEY_SELECTED = "savedTags.selected"
            const val KEY_RENAMING = "savedTags.renaming"
            const val PAGE_SIZE = 30
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The pages of the selected tag ([key]) loaded so far. */
private data class TaggedPages(
    val key: String? = null,
    val items: ImmutableList<TaggedItem> = persistentListOf(),
    val total: Int = 0,
    val nextFromMessageId: Long = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: DomainError? = null,
)

/** Whether a next page may be asked for: there is one, none is on its way, and the last did not fail. */
private val TaggedPages.canLoadMore: Boolean get() = hasMore && !isLoading && error == null

private fun TaggedPages.toResults(selected: String?): TaggedResults =
    if (key == null || key != selected) {
        TaggedResults(isLoading = selected != null)
    } else {
        TaggedResults(items, total, hasMore, isLoading, error)
    }

private fun SavedMessagesTag.toChip() = SavedTagChip(reaction, label, count)

private fun Message.toItem(myUserId: Long?) =
    TaggedItem(messageId = id, preview = messagePreviewText(this, myUserId) { null }, date = date)

/** The saved state's name of a tag's reaction: "e:❤" for an emoji, "c:123" for a custom emoji. */
internal val SavedTagChip.key: String
    get() =
        when (val reaction = reaction) {
            is ReactionType.Emoji -> "e:${reaction.emoji}"
            is ReactionType.CustomEmoji -> "c:${reaction.customEmojiId}"
            ReactionType.Paid -> "p"
        }

private fun reactionOf(key: String): ReactionType? =
    when {
        key.startsWith("e:") -> ReactionType.Emoji(key.removePrefix("e:"))
        key.startsWith("c:") -> key.removePrefix("c:").toLongOrNull()?.let(ReactionType::CustomEmoji)
        else -> null
    }

/** Saved Messages' tags: the chips, the [selected] one (null for "All"), its messages, and the rename dialog. */
@Immutable
data class SavedTagsUiState(
    val tags: ImmutableList<SavedTagChip> = persistentListOf(),
    val selected: SavedTagChip? = null,
    val results: TaggedResults = TaggedResults(),
    val rename: SavedTagRename? = null,
)

/** A tag: its [reaction], its [label] (empty without one) and how many messages it tags. */
@Immutable
data class SavedTagChip(
    val reaction: ReactionType,
    val label: String,
    val count: Int,
)

/**
 * The selected tag's messages loaded so far ([items], newest first), how many it tags in all ([total]), whether more
 * pages follow, one loads, or the last failed ([error]).
 */
@Immutable
data class TaggedResults(
    val items: ImmutableList<TaggedItem> = persistentListOf(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: DomainError? = null,
)

/** A tagged message: its id, what it says ([preview], as the chat list words it) and when it was saved (Unix time). */
@Immutable
data class TaggedItem(
    val messageId: Long,
    val preview: UiText,
    val date: Long,
)

/** The rename dialog of [tag], waiting for Telegram while [isSaving]. */
@Immutable
data class SavedTagRename(
    val tag: SavedTagChip,
    val isSaving: Boolean = false,
)

/** Snackbars of Saved Messages' tags. */
sealed interface SavedTagsEffect {
    data object Renamed : SavedTagsEffect

    data class ShowError(
        val error: DomainError,
    ) : SavedTagsEffect
}
