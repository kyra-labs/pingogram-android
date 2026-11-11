package dev.kyralabs.pingogram.core.data.story

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The stories TDLib is posting for the user (board 10c), by their temporary ids, in the order they were posted: each
 * one's copy, the file TDLib uploads for it and how far it is, as [uploads].
 */
internal class StoryPostings(
    scope: CoroutineScope,
) {
    private val posting = MutableStateFlow<PersistentMap<Int, Posting>>(persistentMapOf())

    val uploads: StateFlow<ImmutableList<StoryUpload>> =
        posting
            .map { map -> map.values.map { it.upload }.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    /** TDLib took story [storyId] from the copy at [path], uploading file [fileId] ([NO_FILE] for none). */
    fun add(
        storyId: Int,
        path: String,
        fileId: Int,
        upload: StoryUpload,
    ) {
        posting.update { it.putting(storyId, Posting(path, fileId, upload)) }
    }

    /** Forgets story [storyId], posted or cancelled; the path of its copy, null for a story it did not know. */
    fun remove(storyId: Int): String? = posting.getAndUpdate { it.removing(storyId) }[storyId]?.path

    /** TDLib gave up posting [storyId], for [failure]. */
    fun fail(
        storyId: Int,
        failure: DomainError,
    ) {
        posting.update { map ->
            map[storyId]?.let { map.putting(storyId, it.copy(upload = it.upload.copy(failure = failure))) } ?: map
        }
    }

    /** [uploaded] of [size] bytes of file [fileId] are sent. */
    fun sent(
        fileId: Int,
        uploaded: Long,
        size: Long,
    ) {
        posting.update { map ->
            val entry = map.entries.firstOrNull { it.value.fileId == fileId } ?: return@update map
            val upload = entry.value.upload.copy(uploadedBytes = uploaded, sizeBytes = size)
            map.putting(entry.key, entry.value.copy(upload = upload))
        }
    }

    private data class Posting(
        val path: String,
        val fileId: Int,
        val upload: StoryUpload,
    )

    companion object {
        /** No file to follow: TDLib's story had none (a content it does not upload). */
        const val NO_FILE = 0
    }
}
