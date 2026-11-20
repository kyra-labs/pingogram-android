package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [BookmarkRepository] in a small file of the account's TDLib directory (`bookmarks.txt`), which the account leaving
 * the device deletes with the rest of it (`AccountStorage`); the demo keeps its bookmarks in memory only. As
 * [FileTranscriptRepository] does: read once on the [io] dispatcher, written whole through a temporary file after each
 * change, at most [MAX_BOOKMARKS] kept (the oldest dropped first), and everything deleted when the account logs out
 * (a [TdlibUpdateCache] for that). Nothing of it leaves the device or is logged.
 */
@AccountScoped
class FileBookmarkRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : BookmarkRepository,
    TdlibUpdateCache {
    @Inject
    constructor(
        client: TdlibClient,
        slot: AccountSlot,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        client = client,
        scope = scope,
        file = if (slot.isDemo) null else TdlibDirectories.of(context.filesDir, slot).root.resolve(FILE_NAME),
        io = io,
    )

    /** The tags and the bookmarks; null until read. */
    private val store = MutableStateFlow<BookmarkStore?>(null)
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onSessionEnd { scope.launch { clear() } }
    }

    override val bookmarks: Flow<ImmutableList<Bookmark>> =
        loaded { store -> store.bookmarks.sortedByDescending { it.savedAt }.toImmutableList() }

    override val tags: Flow<ImmutableList<BookmarkTag>> = loaded { it.tags.toImmutableList() }

    override fun bookmark(
        chatId: Long,
        messageId: Long,
    ): Flow<Bookmark?> =
        loaded { store ->
            store.bookmarks.firstOrNull {
                it.chatId == chatId &&
                    it.messageId == messageId
            }
        }

    override suspend fun save(bookmark: Bookmark): Outcome<Unit> =
        change { store ->
            val known = store.tags.map { it.id }.toSet()
            val kept =
                bookmark.copy(
                    tagIds =
                        bookmark.tagIds
                            .filter { it in known }
                            .distinct()
                            .toImmutableList(),
                )
            val others =
                store.bookmarks.filterNot {
                    it.chatId == bookmark.chatId && it.messageId == bookmark.messageId
                }
            store.copy(bookmarks = (others + kept).sortedBy { it.savedAt }.takeLast(MAX_BOOKMARKS))
        }

    override suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> =
        change { store ->
            store.copy(
                bookmarks =
                    store.bookmarks.filterNot {
                        it.chatId == chatId &&
                            it.messageId == messageId
                    },
            )
        }

    override suspend fun addTag(
        name: String,
        color: Int,
    ): Outcome<BookmarkTag> {
        var added: BookmarkTag? = null
        val outcome =
            change { store ->
                val tag =
                    BookmarkTag(id = (store.tags.maxOfOrNull { it.id } ?: 0L) + 1, name = name.trim(), color = color)
                added = tag
                store.copy(tags = store.tags + tag)
            }
        return when (outcome) {
            is Outcome.Success -> Outcome.Success(checkNotNull(added))
            is Outcome.Failure -> outcome
        }
    }

    override suspend fun updateTag(tag: BookmarkTag): Outcome<Unit> =
        change { store ->
            store.copy(tags = store.tags.map { if (it.id == tag.id) tag.copy(name = tag.name.trim()) else it })
        }

    override suspend fun removeTag(id: Long): Outcome<Unit> =
        change { store ->
            store.copy(
                tags = store.tags.filterNot { it.id == id },
                bookmarks =
                    store.bookmarks.map {
                        it.copy(
                            tagIds =
                                it.tagIds
                                    .filterNot { tag ->
                                        tag == id
                                    }.toImmutableList(),
                        )
                    },
            )
        }

    override suspend fun reorderTags(ids: List<Long>): Outcome<Unit> =
        change { store ->
            val byId = store.tags.associateBy { it.id }
            val ordered = ids.mapNotNull(byId::get)
            store.copy(tags = ordered + store.tags.filterNot { it in ordered })
        }

    private fun <T> loaded(map: (BookmarkStore) -> T): Flow<T> =
        flow {
            if (store.value ==
                null
            ) {
                mutex.withLock { store.value ?: withContext(io) { readBookmarks(file) }.also { store.value = it } }
            }
            emitAll(store.filterNotNull().map(map))
        }.distinctUntilChanged()

    private suspend fun change(transform: (BookmarkStore) -> BookmarkStore): Outcome<Unit> =
        mutex.withLock {
            val next = transform(store.value ?: withContext(io) { readBookmarks(file) }.also { store.value = it })
            store.value = next
            try {
                file?.let { target -> withContext(io) { writeBookmarks(target, next) } }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Bookmarks not saved: ${error.message}"))
            }
        }

    private suspend fun clear() {
        mutex.withLock {
            store.value = BookmarkStore()
            withContext(io) { file?.delete() }
        }
    }

    internal companion object {
        const val FILE_NAME = "bookmarks.txt"

        /** The most bookmarks kept: a few hundred kilobytes at most. */
        const val MAX_BOOKMARKS = 2_000
    }
}

/** The tags in the user's order and the bookmarks, the oldest first. */
private data class BookmarkStore(
    val tags: List<BookmarkTag> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
)

private fun readBookmarks(file: File?): BookmarkStore {
    val lines =
        try {
            file?.takeIf { it.isFile }?.readLines().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
    return BookmarkStore(tags = lines.mapNotNull(::decodeTag), bookmarks = lines.mapNotNull(::decodeBookmark))
}

/** Writes [store] whole through a temporary file, so a write cut short leaves the last good one. */
private fun writeBookmarks(
    target: File,
    store: BookmarkStore,
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    val text =
        buildString {
            store.tags.forEach { append(encode(it)).append('\n') }
            store.bookmarks.forEach { append(encode(it)).append('\n') }
        }
    temporary.writeText(text)
    if (!temporary.renameTo(target)) throw IOException("Could not replace ${target.name}")
}

private const val TAG = "T"
private const val BOOKMARK = "B"
private const val TAG_FIELDS = 4
private const val BOOKMARK_FIELDS = 8

/** A tag's line: "T", its id, its color and its name, tab-separated, the name escaped. */
private fun encode(tag: BookmarkTag) = "$TAG\t${tag.id}\t${tag.color}\t${escape(tag.name)}"

/** A bookmark's line: "B", the chat, the message, when, the tags, the sender, the text and the note. */
private fun encode(bookmark: Bookmark) =
    listOf(
        BOOKMARK,
        bookmark.chatId,
        bookmark.messageId,
        bookmark.savedAt,
        bookmark.tagIds.joinToString(","),
        escape(bookmark.preview.sender),
        escape(bookmark.preview.text),
        escape(bookmark.note),
    ).joinToString("\t")

private fun decodeTag(line: String): BookmarkTag? {
    val fields = line.split('\t', limit = TAG_FIELDS).takeIf { it.size == TAG_FIELDS && it[0] == TAG } ?: return null
    val id = fields[1].toLongOrNull()
    val color = fields[2].toIntOrNull()
    return if (id != null && color != null) BookmarkTag(id, unescape(fields[NAME]), color) else null
}

private fun decodeBookmark(line: String): Bookmark? {
    val fields =
        line.split('\t', limit = BOOKMARK_FIELDS).takeIf { it.size == BOOKMARK_FIELDS && it[0] == BOOKMARK }
            ?: return null
    val chatId = fields[1].toLongOrNull()
    val messageId = fields[2].toLongOrNull()
    val savedAt = fields[SAVED_AT].toLongOrNull()
    return if (chatId != null && messageId != null && savedAt != null) {
        Bookmark(
            chatId = chatId,
            messageId = messageId,
            savedAt = savedAt,
            tagIds = fields[TAG_IDS].split(',').mapNotNull { it.toLongOrNull() }.toImmutableList(),
            note = unescape(fields[NOTE]),
            preview = BookmarkPreview(sender = unescape(fields[SENDER]), text = unescape(fields[TEXT])),
        )
    } else {
        null
    }
}

private const val NAME = 3
private const val SAVED_AT = 3
private const val TAG_IDS = 4
private const val SENDER = 5
private const val TEXT = 6
private const val NOTE = 7
