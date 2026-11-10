package dev.kyralabs.pingogram.feature.media.storypost

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The picked photos and videos of a new story, in their order, and the one shown: kept in [savedState] for process
 * death, their copies deleted through [attachments] as they are removed or [discardAll]ed. [NewStoryViewModel]'s list.
 */
internal class StoryPicks(
    private val savedState: SavedStateHandle,
    private val attachments: AttachmentRepository,
) {
    private val mutableItems = MutableStateFlow(savedState.restoredItems())
    private val mutableSelected = MutableStateFlow(savedState.restoredSelected())

    val items: StateFlow<List<Attachment>> = mutableItems.asStateFlow()
    val selected: StateFlow<String?> = mutableSelected.asStateFlow()

    /** Adds [attachment] at the end and shows it. */
    fun add(attachment: Attachment) = set(mutableItems.value + attachment, attachment.path)

    /** Shows item [key]. */
    fun select(key: String) {
        if (mutableItems.value.any { it.path == key }) set(mutableItems.value, key)
    }

    /** Removes item [key], deleting its copy; the item after it shows if it was shown, else the one before. */
    fun remove(key: String) {
        val list = mutableItems.value
        val removed = list.firstOrNull { it.path == key } ?: return
        attachments.discard(listOf(removed))
        val index = list.indexOf(removed)
        val rest = list - removed
        val selected =
            mutableSelected.value.takeIf { it != key } ?: rest.getOrNull(index.coerceAtMost(rest.lastIndex))?.path
        set(rest, selected)
    }

    /** Moves item [key] to position [to]. */
    fun move(
        key: String,
        to: Int,
    ) {
        val list = mutableItems.value
        val from = list.indexOfFirst { it.path == key }
        if (from < 0 || to !in list.indices || from == to) return
        set(list.toMutableList().apply { add(to, removeAt(from)) }, mutableSelected.value)
    }

    /** Deletes every copy, and the camera's file if the camera app is open, and empties the list. */
    fun discardAll() {
        if (mutableItems.value.isNotEmpty()) attachments.discard(mutableItems.value)
        savedState.cameraFile?.let(attachments::discardCameraFile)
        savedState.cameraFile = null
        set(emptyList(), null)
    }

    private fun set(
        list: List<Attachment>,
        selected: String?,
    ) {
        mutableItems.value = list
        mutableSelected.value = selected?.takeIf { key -> list.any { it.path == key } }
        savedState.saveItems(list, mutableSelected.value)
    }
}

/** The screen's state of [items], the shown one [selected] and [preparing] still being copied. */
internal fun uiStateOf(
    items: List<Attachment>,
    selected: String?,
    preparing: Int,
): NewStoryUiState =
    NewStoryUiState(
        items = items.mapNotNull(::pickOf).toImmutableList(),
        selected = selected,
        preparing = preparing,
    )

private fun pickOf(attachment: Attachment): StoryPick? =
    when (attachment) {
        is Attachment.Photo -> StoryPick(attachment.path, attachment.path, isVideo = false)
        is Attachment.Video -> StoryPick(attachment.path, attachment.thumbnail?.path, true, attachment.duration)
        is Attachment.Document -> null
    }
