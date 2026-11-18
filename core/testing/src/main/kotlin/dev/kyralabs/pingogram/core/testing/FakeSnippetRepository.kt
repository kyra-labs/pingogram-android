package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.SnippetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [SnippetRepository] in memory: the [saved] snippets in order; writes fail with [failure] while it is set. */
class FakeSnippetRepository : SnippetRepository {
    val saved = MutableStateFlow<List<Snippet>>(emptyList())
    var failure: DomainError? = null

    override val snippets: Flow<ImmutableList<Snippet>> = saved.map { it.toImmutableList() }

    override suspend fun add(
        name: String,
        text: String,
    ): Outcome<Snippet> {
        failure?.let { return Outcome.Failure(it) }
        val snippet = Snippet((saved.value.maxOfOrNull { it.id } ?: 0L) + 1, name.trim(), text)
        saved.value += snippet
        return Outcome.Success(snippet)
    }

    override suspend fun update(snippet: Snippet): Outcome<Unit> =
        write { saved.value = saved.value.map { if (it.id == snippet.id) snippet else it } }

    override suspend fun remove(id: Long): Outcome<Unit> = write { saved.value = saved.value.filterNot { it.id == id } }

    override suspend fun reorder(ids: List<Long>): Outcome<Unit> =
        write {
            val byId = saved.value.associateBy { it.id }
            val ordered = ids.mapNotNull(byId::get)
            saved.value = ordered + saved.value.filterNot { it in ordered }
        }

    private fun write(block: () -> Unit): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        block()
        return Outcome.Success(Unit)
    }
}
