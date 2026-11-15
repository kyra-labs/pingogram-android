package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [BookmarkRepository] in memory: [saved] bookmarks and [savedTags]; writes fail with [failure] while it is set. */
class FakeBookmarkRepository : BookmarkRepository {
    val saved = MutableStateFlow<List<Bookmark>>(emptyList())
    val savedTags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    var failure: DomainError? = null

    override val bookmarks: Flow<ImmutableList<Bookmark>> =
        saved.map { all -> all.sortedByDescending { it.savedAt }.toImmutableList() }.distinctUntilChanged()

    override val tags: Flow<ImmutableList<BookmarkTag>> = savedTags.map { it.toImmutableList() }

    override fun bookmark(
        chatId: Long,
        messageId: Long,
    ): Flow<Bookmark?> =
        saved.map { all -> all.firstOrNull { it.chatId == chatId && it.messageId == messageId } }.distinctUntilChanged()

    override suspend fun save(bookmark: Bookmark): Outcome<Unit> =
        write {
            saved.value =
                saved.value.filterNot { it.chatId == bookmark.chatId && it.messageId == bookmark.messageId } + bookmark
        }

    override suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> =
        write { saved.value = saved.value.filterNot { it.chatId == chatId && it.messageId == messageId } }

    override suspend fun addTag(
        name: String,
        color: Int,
    ): Outcome<BookmarkTag> {
        failure?.let { return Outcome.Failure(it) }
        val tag = BookmarkTag((savedTags.value.maxOfOrNull { it.id } ?: 0L) + 1, name.trim(), color)
        savedTags.value += tag
        return Outcome.Success(tag)
    }

    override suspend fun updateTag(tag: BookmarkTag): Outcome<Unit> =
        write { savedTags.value = savedTags.value.map { if (it.id == tag.id) tag else it } }

    override suspend fun removeTag(id: Long): Outcome<Unit> =
        write {
            savedTags.value = savedTags.value.filterNot { it.id == id }
            saved.value =
                saved.value.map { it.copy(tagIds = it.tagIds.filterNot { tag -> tag == id }.toImmutableList()) }
        }

    override suspend fun reorderTags(ids: List<Long>): Outcome<Unit> =
        write {
            val byId = savedTags.value.associateBy { it.id }
            val ordered = ids.mapNotNull(byId::get)
            savedTags.value = ordered + savedTags.value.filterNot { it in ordered }
        }

    private inline fun write(change: () -> Unit): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        change()
        return Outcome.Success(Unit)
    }
}
