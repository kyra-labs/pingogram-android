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
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.SnippetRepository
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
 * [SnippetRepository] in a small file of the account's TDLib directory (`snippets.txt`), which the account leaving
 * the device deletes with the rest of it; the demo keeps its snippets in memory only. As [FileBookmarkRepository]
 * does: read once on the [io] dispatcher, written whole through a temporary file after each change, and deleted when
 * the account logs out (a [TdlibUpdateCache] for that). Nothing of it leaves the device or is logged.
 */
@AccountScoped
class FileSnippetRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : SnippetRepository,
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

    /** The snippets in the user's order; null until read. */
    private val store = MutableStateFlow<List<Snippet>?>(null)
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onSessionEnd { scope.launch { clear() } }
    }

    override val snippets: Flow<ImmutableList<Snippet>> =
        flow {
            if (store.value == null) mutex.withLock { loaded() }
            emitAll(store.filterNotNull().map { it.toImmutableList() })
        }.distinctUntilChanged()

    override suspend fun add(
        name: String,
        text: String,
    ): Outcome<Snippet> {
        var added: Snippet? = null
        val outcome =
            change { snippets ->
                if (snippets.size >= Snippet.MAX_SNIPPETS) return@change null
                val snippet = Snippet((snippets.maxOfOrNull { it.id } ?: 0L) + 1, name.trim(), text)
                added = snippet
                (snippets + snippet).takeIf { it.isValid() }
            }
        return when (outcome) {
            is Outcome.Success -> Outcome.Success(checkNotNull(added))
            is Outcome.Failure -> outcome
        }
    }

    override suspend fun update(snippet: Snippet): Outcome<Unit> =
        change { snippets ->
            snippets
                .map { if (it.id == snippet.id) snippet.copy(name = snippet.name.trim()) else it }
                .takeIf { it.isValid() }
        }

    override suspend fun remove(id: Long): Outcome<Unit> = change { snippets -> snippets.filterNot { it.id == id } }

    override suspend fun reorder(ids: List<Long>): Outcome<Unit> =
        change { snippets ->
            val byId = snippets.associateBy { it.id }
            val ordered = ids.distinct().mapNotNull(byId::get)
            ordered + snippets.filterNot { it in ordered }
        }

    /** Reads the file once; call with [mutex] held. */
    private suspend fun loaded(): List<Snippet> =
        store.value ?: withContext(io) { readSnippets(file) }.also { store.value = it }

    /** Applies [transform], which answers null for a change the rules refuse, and writes the result. */
    private suspend fun change(transform: (List<Snippet>) -> List<Snippet>?): Outcome<Unit> =
        mutex.withLock {
            val next = transform(loaded()) ?: return Outcome.Failure(DomainError.Unknown(code = 0, message = REFUSED))
            store.value = next
            try {
                file?.let { target -> withContext(io) { writeSnippets(target, next) } }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Snippets not saved: ${error.message}"))
            }
        }

    private suspend fun clear() {
        mutex.withLock {
            store.value = emptyList()
            withContext(io) { file?.delete() }
        }
    }

    internal companion object {
        const val FILE_NAME = "snippets.txt"
        private const val REFUSED = "Snippet refused: a name taken or not allowed, an empty text, or too many"
    }
}

/** Whether every name is allowed and taken once, letter case aside, and every text fits. */
private fun List<Snippet>.isValid(): Boolean =
    all { Snippet.isValidName(it.name) && it.text.isNotBlank() && it.text.length <= Snippet.MAX_TEXT_LENGTH } &&
        map { it.name.lowercase() }.toSet().size == size

private fun readSnippets(file: File?): List<Snippet> {
    val lines =
        try {
            file?.takeIf { it.isFile }?.readLines().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
    return lines.mapNotNull(::decode)
}

/** Writes [snippets] whole through a temporary file, so a write cut short leaves the last good one. */
private fun writeSnippets(
    target: File,
    snippets: List<Snippet>,
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    temporary.writeText(snippets.joinToString("") { encode(it) + "\n" })
    if (!temporary.renameTo(target)) throw IOException("Could not replace ${target.name}")
}

private const val FIELDS = 3

/** A snippet's line: its id, its name and its text, tab-separated, the text escaped. */
private fun encode(snippet: Snippet) = "${snippet.id}\t${snippet.name}\t${escape(snippet.text)}"

private fun decode(line: String): Snippet? {
    val fields = line.split('\t', limit = FIELDS)
    val id = fields.first().toLongOrNull()
    return if (fields.size == FIELDS && id != null) Snippet(id, fields[1], unescape(fields[2])) else null
}
